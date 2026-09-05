package lifecycle

import (
	"context"
	"sync"
)

type Coordinator struct {
	context context.Context
	cancel  context.CancelCauseFunc
	once    sync.Once
}

func New(parent context.Context) *Coordinator {
	context, cancel := context.WithCancelCause(parent)
	return &Coordinator{context: context, cancel: cancel}
}

func (coordinator *Coordinator) Context() context.Context {
	return coordinator.context
}

func (coordinator *Coordinator) Fatal(err error) {
	if err == nil {
		return
	}
	coordinator.once.Do(func() { coordinator.cancel(err) })
}

func (coordinator *Coordinator) Stop() {
	coordinator.cancel(nil)
}

func (coordinator *Coordinator) Cause() error {
	return context.Cause(coordinator.context)
}
