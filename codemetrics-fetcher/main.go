// main.go
package main

import (
	"database/sql"
	"fmt"
	"log"
	"sync"
	"time"

	_ "github.com/lib/pq"
)

var db *sql.DB

const maxConcurrentBranches = 5

func initDB() {
	connStr := "host=postgres port=5432 user=metrics_user password=1234 dbname=metrics_db sslmode=disable"
	var err error
	db, err = sql.Open("postgres", connStr)
	if err != nil {
		log.Fatalf("❌ DB open error: %v", err)
	}
	if err := db.Ping(); err != nil {
		log.Fatalf("❌ DB ping error: %v", err)
	}
	fmt.Println("✅ PostgreSQL connected")
}

func main() {
	initDB()
	interval := 10 * time.Minute
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	fmt.Printf("🚀 Starting PostgreSQL collector (interval: %v)\n", interval)

	collect()

	for {
		select {
		case <-ticker.C:
			fmt.Println("\n🔄 Running scheduled collection...")
			collect()
		}
	}
}

func collect() {
	projects, err := fetchProjects()
	if err != nil {
		log.Printf("❌ Fetch projects: %v", err)
		return
	}
	fmt.Printf("✅ Got %d projects\n", len(projects))

	for _, proj := range projects {
		fmt.Printf("📂 Processing project: %s\n", proj.Name)

		projectDetails, err := fetchProjectDetails(proj.Name)
		if err != nil {
			log.Printf("  ❌ Skip project details for %s: %v", proj.Name, err)
			continue
		}

		var projectID string
		err = db.QueryRow(`
			INSERT INTO projects (name, full_name, description, created_at, updated_at)
			VALUES ($1, $2, $3, $4, $5)
			ON CONFLICT (name) DO UPDATE SET
				full_name = EXCLUDED.full_name,
				description = EXCLUDED.description,
				updated_at = EXCLUDED.updated_at
			RETURNING id
		`, projectDetails.Name, projectDetails.FullName, projectDetails.Description,
			projectDetails.CreatedAt, projectDetails.UpdatedAt).Scan(&projectID)
		if err != nil {
			log.Printf("  ❌ Save project %s: %v", proj.Name, err)
			continue
		}

		repos, err := fetchRepos(proj.Name)
		if err != nil {
			log.Printf("  ⚠️ Skip repos for %s: %v", proj.Name, err)
			continue
		}

		for _, repo := range repos {
			fmt.Printf("    📁 Processing repo: %s\n", repo.Name)

			repoDetails, err := fetchRepositoryDetails(proj.Name, repo.Name)
			if err != nil {
				log.Printf("      ⚠️ Skip repo details for %s/%s: %v", proj.Name, repo.Name, err)
				repoDetails = &RepositoryDetails{Name: repo.Name}
			}

			branches, err := fetchBranches(proj.Name, repo.Name)
			if err != nil {
				log.Printf("      ⚠️ Skip branches for %s/%s: %v", proj.Name, repo.Name, err)
				branches = []Branch{{Name: "HEAD"}}
			}
			activeBranches := len(branches)

			var repoID string
			err = db.QueryRow(`
				INSERT INTO repositories (name, description, active_branches, project_id)
				VALUES ($1, $2, $3, $4)
				ON CONFLICT (name, project_id) DO UPDATE SET
					description = EXCLUDED.description,
					active_branches = EXCLUDED.active_branches
				RETURNING id
			`, repoDetails.Name, repoDetails.Description, activeBranches, projectID).Scan(&repoID)
			if err != nil {
				log.Printf("      ❌ Save repo %s: %v", repo.Name, err)
				continue
			}

			// Parallel branch processing
			var wg sync.WaitGroup
			branchChan := make(chan Branch, len(branches))
			semaphore := make(chan struct{}, maxConcurrentBranches)

			for _, b := range branches {
				branchChan <- b
			}
			close(branchChan)

			for branch := range branchChan {
				wg.Add(1)
				semaphore <- struct{}{}

				go func(b Branch, repoID string) {
					defer wg.Done()
					defer func() { <-semaphore }()

					// Fetch ALL commits with pagination
					var allCommits []CommitListItem
					cursor := ""
					pageCount := 0

					for {
						page, err := fetchCommitsPage(proj.Name, repo.Name, b.Name, cursor)
						if err != nil {
							log.Printf("          ⚠️ Skip commits page for branch %s (cursor=%s): %v", b.Name, cursor, err)
							break
						}

						allCommits = append(allCommits, page.Data...)
						pageCount++
						fmt.Printf("          ➕ Page %d: %d commits (total: %d)\n", pageCount, len(page.Data), len(allCommits))

						if page.Page.NextCursor == "" {
							break
						}
						cursor = page.Page.NextCursor

						if len(allCommits) > 100000 {
							log.Printf("          ⚠️ Too many commits (>100k), stop for branch %s", b.Name)
							break
						}
					}

					// Process all commits
					for _, cItem := range allCommits {
						if cItem.Hash == "" {
							continue
						}

						time.Sleep(200 * time.Millisecond)

						fullCommit, err := fetchFullCommit(proj.Name, repo.Name, cItem.Hash)
						if err != nil {
							log.Printf("          ❌ Skip full commit %s: %v", cItem.Hash, err)
							continue
						}

						diff, err := fetchCommitDiff(proj.Name, repo.Name, cItem.Hash)
						if err != nil {
							log.Printf("          ❌ Skip diff for %s: %v", cItem.Hash, err)
							continue
						}

						author := fullCommit.GetAuthor()
						if author.Email == "" {
							author.Email = "unknown@example.com"
						}
						if author.Name == "" {
							author.Name = "Unknown"
						}

						var devID string
						err = db.QueryRow(`
							INSERT INTO developers (name, email)
							VALUES ($1, $2)
							ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name
							RETURNING id
						`, author.Name, author.Email).Scan(&devID)
						if err != nil {
							log.Printf("          ❌ Save developer %s: %v", author.Email, err)
							continue
						}

						added, deleted := parseDiffStats(diff)
						_, err = db.Exec(`
							INSERT INTO commits (hash, message, created_at, branch_name, lines_added, lines_deleted, developer_id, project_id, repository_id)
							VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
							ON CONFLICT (hash) DO NOTHING
						`, cItem.Hash, fullCommit.Message, fullCommit.CreatedAt,
							b.Name, added, deleted,
							devID, projectID, repoID)
						if err != nil {
							log.Printf("          ❌ Save commit %s: %v", cItem.Hash, err)
						} else {
							fmt.Printf("          💾 Saved commit %s\n", cItem.Hash[:8])
						}
					}
				}(branch, repoID)
			}

			wg.Wait()
		}
	}

	fmt.Println("✅ Collection finished")
}

func (c *FullCommit) GetAuthor() GitUser {
	if c.Author.Name != "" || c.Author.Email != "" {
		return c.Author
	}
	return GitUser{Name: "Unknown", Email: "unknown@example.com"}
}
