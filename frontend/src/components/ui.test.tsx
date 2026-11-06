import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button, Modal, RiskBadge, StatusBadge } from './ui'

describe('ui components', () => {
  it('shows staff and customer wording for statuses', () => {
    const { rerender } = render(<StatusBadge status="INFO_REQUESTED" />)
    expect(screen.getByText('Info requested')).toBeInTheDocument()
    rerender(<StatusBadge status="INFO_REQUESTED" customer />)
    expect(screen.getByText('Action needed')).toBeInTheDocument()
  })

  it('shows risk with score', () => {
    render(<RiskBadge level="HIGH" score={82} />)
    expect(screen.getByText('High risk')).toBeInTheDocument()
    expect(screen.getByText(/82/)).toBeInTheDocument()
  })

  it('disables a loading button', async () => {
    const onClick = vi.fn()
    render(<Button loading onClick={onClick}>Save</Button>)
    await userEvent.click(screen.getByRole('button', { name: /save/i }))
    expect(onClick).not.toHaveBeenCalled()
  })

  it('closes a modal with Escape', async () => {
    const onClose = vi.fn()
    render(<Modal open onClose={onClose} title="Approve claim"><p>body</p></Modal>)
    expect(screen.getByRole('dialog', { name: 'Approve claim' })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalled()
  })
})
