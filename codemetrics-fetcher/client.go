// client.go
package main

import (
	"bufio"
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const BaseURL = "https://gateway-codemetrics.saas.sferaplatform.ru/app/sourcecode/api/api/v2"

// ⚠️ Замени на свои данные
const Username = "fenikstop@mail.ru"
const Password = "WOptSIsCqk29"

func doRequestWithCtx(ctx context.Context, method, urlStr string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, method, urlStr, nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.SetBasicAuth(Username, Password)

	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	client := &http.Client{
		Transport: tr,
	}

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("send request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read body: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
	}
	return body, nil
}

// Projects
func fetchProjects() ([]ProjectListItem, error) {
	data, err := doRequestWithCtx(context.Background(), "GET", BaseURL+"/projects")
	if err != nil {
		return nil, err
	}
	var resp ProjectsListResponse
	json.Unmarshal(data, &resp)
	return resp.Data, nil
}

func fetchProjectDetails(projectKey string) (*ProjectDetails, error) {
	data, err := doRequestWithCtx(context.Background(), "GET", BaseURL+"/projects/"+projectKey)
	if err != nil {
		return nil, err
	}
	var resp ProjectDetailsResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parse project details: %w", err)
	}
	return &resp.Data, nil
}

// Repos
func fetchRepos(projectKey string) ([]RepositoryListItem, error) {
	urlStr := fmt.Sprintf("%s/projects/%s/repos", BaseURL, projectKey)
	data, err := doRequestWithCtx(context.Background(), "GET", urlStr)
	if err != nil {
		return nil, err
	}
	var resp ReposResponse
	json.Unmarshal(data, &resp)
	return resp.Data, nil
}

func fetchRepositoryDetails(projectKey, repoName string) (*RepositoryDetails, error) {
	urlStr := fmt.Sprintf("%s/projects/%s/repos/%s", BaseURL, projectKey, repoName)
	data, err := doRequestWithCtx(context.Background(), "GET", urlStr)
	if err != nil {
		return nil, err
	}
	var resp RepositoryDetailsResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parse repo details: %w", err)
	}
	return &resp.Data, nil
}

// Branches
func fetchBranches(projectKey, repoName string) ([]Branch, error) {
	urlStr := fmt.Sprintf("%s/projects/%s/repos/%s/branches", BaseURL, projectKey, repoName)
	data, err := doRequestWithCtx(context.Background(), "GET", urlStr)
	if err != nil {
		return nil, err
	}
	var resp BranchesResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parse branches: %w", err)
	}
	return resp.Data, nil
}

// Commits with pagination
func fetchCommitsPage(projectKey, repoName, rev, cursor string) (*PaginatedCommitsResponse, error) {
	urlStr := fmt.Sprintf("%s/projects/%s/repos/%s/commits", BaseURL, projectKey, repoName)
	params := []string{}
	if rev != "" {
		params = append(params, "rev="+url.QueryEscape(rev))
	}
	if cursor != "" {
		params = append(params, "cursor="+url.QueryEscape(cursor))
	}
	if len(params) > 0 {
		urlStr += "?" + strings.Join(params, "&")
	}

	data, err := doRequestWithCtx(context.Background(), "GET", urlStr)
	if err != nil {
		return nil, err
	}

	var resp PaginatedCommitsResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parse commits page: %w", err)
	}
	return &resp, nil
}

func fetchFullCommit(projectKey, repoName, ref string) (*FullCommit, error) {
	urlStr := fmt.Sprintf("%s/projects/%s/repos/%s/commits/%s", BaseURL, projectKey, repoName, ref)
	ctx, cancel := context.WithTimeout(context.Background(), 80*time.Second)
	defer cancel()
	data, err := doRequestWithCtx(ctx, "GET", urlStr)
	if err != nil {
		return nil, fmt.Errorf("full commit timeout (20s) or error: %w", err)
	}
	var resp FullCommitResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parse full commit: %w", err)
	}
	return &resp.Data, nil
}

func fetchCommitDiff(projectKey, repoName, hash string) (string, error) {
	urlStr := fmt.Sprintf("%s/projects/%s/repos/%s/commits/%s/diff", BaseURL, projectKey, repoName, hash)
	ctx, cancel := context.WithTimeout(context.Background(), 80*time.Second)
	defer cancel()
	data, err := doRequestWithCtx(ctx, "GET", urlStr)
	if err != nil {
		return "", fmt.Errorf("diff timeout (20s) or error: %w", err)
	}
	var resp DiffResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return "", fmt.Errorf("parse diff response: %w", err)
	}
	decoded, err := base64.StdEncoding.DecodeString(resp.Data.Content)
	if err != nil {
		return "", fmt.Errorf("decode base64 diff: %w", err)
	}
	return string(decoded), nil
}

func parseDiffStats(diff string) (added, deleted int) {
	scanner := bufio.NewScanner(strings.NewReader(diff))
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "+") && !strings.HasPrefix(line, "+++") {
			added++
		} else if strings.HasPrefix(line, "-") && !strings.HasPrefix(line, "---") {
			deleted++
		}
	}
	return added, deleted
}
