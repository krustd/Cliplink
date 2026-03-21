package clipboard

import "github.com/atotto/clipboard"

type Writer interface {
	WriteText(text string) error
}

type systemWriter struct{}

func NewSystemWriter() Writer {
	return systemWriter{}
}

func (systemWriter) WriteText(text string) error {
	return clipboard.WriteAll(text)
}
