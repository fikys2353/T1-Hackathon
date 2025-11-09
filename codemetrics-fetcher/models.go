// models.go
package main

import "time"

type ProjectListItem struct {
	Name string `json:"name"`
}

type ProjectDetails struct {
	Name        string    `json:"name"`
	FullName    string    `json:"full_name"`
	Description string    `json:"description"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type RepositoryListItem struct {
	Name string `json:"name"`
}

type RepositoryDetails struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

type Branch struct {
	Name string `json:"name"`
}

type CommitListItem struct {
	Hash string `json:"hash"`
}

type GitUser struct {
	Name  string `json:"name"`
	Email string `json:"email"`
}

type FullCommit struct {
	Hash      string    `json:"hash"`
	Author    GitUser   `json:"author"`
	Message   string    `json:"message"`
	CreatedAt time.Time `json:"created_at"`
}

// === API Response Wrappers ===
type ProjectsListResponse struct {
	Data []ProjectListItem `json:"data"`
}

type ProjectDetailsResponse struct {
	Data ProjectDetails `json:"data"`
}

type ReposResponse struct {
	Data []RepositoryListItem `json:"data"`
}

type RepositoryDetailsResponse struct {
	Data RepositoryDetails `json:"data"`
}

type BranchesResponse struct {
	Data []Branch `json:"data"`
}

type FullCommitResponse struct {
	Data FullCommit `json:"data"`
}

type DiffResponse struct {
	Data struct {
		Content string `json:"content"`
	} `json:"data"`
}

// Пагинация для коммитов
type PaginatedCommitsResponse struct {
	Page struct {
		NextCursor string `json:"next_cursor"`
	} `json:"page"`
	Data []CommitListItem `json:"data"`
}
