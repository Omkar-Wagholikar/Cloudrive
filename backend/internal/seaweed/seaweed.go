// Package seaweed provides a minimal client for SeaweedFS.
// It uses the HTTP API directly (no extra SDK needed) so the binary stays small.
package seaweed

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"strings"
	"time"
)

var client = &http.Client{Timeout: 60 * time.Second}

type assignResp struct {
	FID       string `json:"fid"`
	URL       string `json:"url"`       // volume server host:port
	PublicURL string `json:"publicUrl"` // same as URL in simple setups
	Count     int    `json:"count"`
	Error     string `json:"error"`
}

// Assign asks the master for a new file ID and volume server URL.
func Assign(master string) (fid, volURL string, err error) {
	resp, err := client.Get("http://" + master + "/dir/assign")
	if err != nil {
		return "", "", fmt.Errorf("assign: %w", err)
	}
	defer resp.Body.Close()

	var a assignResp
	if err := json.NewDecoder(resp.Body).Decode(&a); err != nil {
		return "", "", fmt.Errorf("assign decode: %w", err)
	}
	if a.Error != "" {
		return "", "", fmt.Errorf("assign error: %s", a.Error)
	}
	return a.FID, a.URL, nil
}

// Upload writes r to SeaweedFS and returns the fid to store in the DB.
// filename and mimeType are passed to the multipart form so SeaweedFS
// stores the right content-type.
func Upload(master, filename, mimeType string, r io.Reader) (fid string, err error) {
	fid, volURL, err := Assign(master)
	if err != nil {
		return "", err
	}

	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)

	fw, err := mw.CreateFormFile("file", filename)
	if err != nil {
		return "", err
	}
	if _, err := io.Copy(fw, r); err != nil {
		return "", err
	}
	mw.Close()

	req, _ := http.NewRequest(http.MethodPost,
		"http://"+volURL+"/"+fid, &buf)
	req.Header.Set("Content-Type", mw.FormDataContentType())

	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("seaweed upload: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("seaweed upload status %d: %s", resp.StatusCode, body)
	}
	return fid, nil
}

// Download fetches a file from SeaweedFS by FID.
// It first asks the master for the volume server, then fetches the file.
func Download(master, fid string) (io.ReadCloser, string, error) {
	// Lookup volume server
	volURL, err := lookupVolume(master, volumeID(fid))
	if err != nil {
		return nil, "", err
	}

	resp, err := client.Get("http://" + volURL + "/" + fid)
	if err != nil {
		return nil, "", fmt.Errorf("seaweed download: %w", err)
	}
	if resp.StatusCode == http.StatusNotFound {
		resp.Body.Close()
		return nil, "", fmt.Errorf("not found")
	}
	if resp.StatusCode >= 300 {
		resp.Body.Close()
		return nil, "", fmt.Errorf("seaweed download status %d", resp.StatusCode)
	}
	ct := resp.Header.Get("Content-Type")
	return resp.Body, ct, nil
}

// Delete removes a file from SeaweedFS.
func Delete(master, fid string) error {
	if fid == "" {
		return nil
	}
	volURL, err := lookupVolume(master, volumeID(fid))
	if err != nil {
		return err
	}
	req, _ := http.NewRequest(http.MethodDelete, "http://"+volURL+"/"+fid, nil)
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	resp.Body.Close()
	return nil
}

type lookupResp struct {
	Locations []struct {
		PublicURL string `json:"publicUrl"`
		URL       string `json:"url"`
	} `json:"locations"`
	Error string `json:"error"`
}

func lookupVolume(master, volumeID string) (string, error) {
	resp, err := client.Get("http://" + master + "/dir/lookup?volumeId=" + volumeID)
	if err != nil {
		return "", fmt.Errorf("lookup: %w", err)
	}
	defer resp.Body.Close()

	var l lookupResp
	if err := json.NewDecoder(resp.Body).Decode(&l); err != nil {
		return "", fmt.Errorf("lookup decode: %w", err)
	}
	if l.Error != "" {
		return "", fmt.Errorf("lookup error: %s", l.Error)
	}
	if len(l.Locations) == 0 {
		return "", fmt.Errorf("no volume locations found")
	}
	return l.Locations[0].URL, nil
}

// volumeID extracts the numeric volume id from a fid like "3,01637037ef".
func volumeID(fid string) string {
	parts := strings.SplitN(fid, ",", 2)
	return parts[0]
}
