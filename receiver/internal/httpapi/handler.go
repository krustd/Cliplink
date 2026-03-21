package httpapi

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"

	"cliplink/receiver/internal/clipboard"
)

const maxTextBytes = 1024 * 1024 // 1MB

type Handler struct {
	deviceName string
	deviceOS   string
	writer     clipboard.Writer
}

type textPushRequest struct {
	Text   string `json:"text"`
	Source string `json:"source,omitempty"`
}

type genericPushRequest struct {
	Type   string `json:"type"`
	Text   string `json:"text,omitempty"`
	Source string `json:"source,omitempty"`
}

func NewHandler(deviceName, deviceOS string, writer clipboard.Writer) http.Handler {
	h := &Handler{
		deviceName: deviceName,
		deviceOS:   deviceOS,
		writer:     writer,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", h.healthz)
	mux.HandleFunc("GET /api/v1/info", h.info)
	mux.HandleFunc("POST /api/v1/clipboard/text", h.pushText)
	mux.HandleFunc("POST /api/v1/clipboard/push", h.pushGeneric)
	return withJSONContentType(mux)
}

func (h *Handler) healthz(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"ok": true,
	})
}

func (h *Handler) info(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":          true,
		"device_name": h.deviceName,
		"device_os":   h.deviceOS,
	})
}

func (h *Handler) pushText(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxTextBytes)
	defer r.Body.Close()

	var req textPushRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		var maxBytesErr *http.MaxBytesError
		if errors.As(err, &maxBytesErr) {
			writeJSON(w, http.StatusRequestEntityTooLarge, map[string]any{
				"error": "payload too large",
			})
			return
		}
		writeJSON(w, http.StatusBadRequest, map[string]any{
			"error": "invalid json",
		})
		return
	}

	req.Text = strings.TrimRight(req.Text, "\r\n")
	if strings.TrimSpace(req.Text) == "" {
		writeJSON(w, http.StatusBadRequest, map[string]any{
			"error": "text is required",
		})
		return
	}

	if err := h.writer.WriteText(req.Text); err != nil {
		log.Printf("write clipboard failed: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{
			"error": "failed to write clipboard",
		})
		return
	}
	log.Printf(
		"clipboard text accepted source=%q len=%d remote=%s",
		req.Source,
		len(req.Text),
		r.RemoteAddr,
	)

	writeJSON(w, http.StatusAccepted, map[string]any{
		"ok":     true,
		"source": req.Source,
		"length": len(req.Text),
	})
}

func (h *Handler) pushGeneric(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxTextBytes)
	defer r.Body.Close()

	var req genericPushRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		var maxBytesErr *http.MaxBytesError
		if errors.As(err, &maxBytesErr) {
			writeJSON(w, http.StatusRequestEntityTooLarge, map[string]any{
				"error": "payload too large",
			})
			return
		}
		writeJSON(w, http.StatusBadRequest, map[string]any{
			"error": "invalid json",
		})
		return
	}

	switch strings.ToLower(strings.TrimSpace(req.Type)) {
	case "", "text":
		text := strings.TrimRight(req.Text, "\r\n")
		if strings.TrimSpace(text) == "" {
			writeJSON(w, http.StatusBadRequest, map[string]any{
				"error": "text is required for type=text",
			})
			return
		}
		if err := h.writer.WriteText(text); err != nil {
			log.Printf("write clipboard failed: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{
				"error": "failed to write clipboard",
			})
			return
		}
		log.Printf(
			"clipboard push accepted type=%q source=%q len=%d remote=%s",
			"text",
			req.Source,
			len(text),
			r.RemoteAddr,
		)
		writeJSON(w, http.StatusAccepted, map[string]any{
			"ok":     true,
			"type":   "text",
			"source": req.Source,
			"length": len(text),
		})
	default:
		writeJSON(w, http.StatusNotImplemented, map[string]any{
			"error":           "unsupported clipboard type for now",
			"supported_types": []string{"text"},
		})
	}
}

func withJSONContentType(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, data any) {
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}
