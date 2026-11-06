import { describe, expect, it } from 'vitest'
import { compactInr, fileSize, inr, relative, titleCase } from './format'

describe('format', () => {
  it('formats rupees with Indian grouping', () => {
    expect(inr(100000)).toBe('₹1,00,000')
    expect(inr(2300000)).toBe('₹23,00,000')
    expect(inr(null)).toBe('—')
  })

  it('compacts lakhs and crores', () => {
    expect(compactInr(250000)).toBe('₹2.50 L')
    expect(compactInr(31000000)).toBe('₹3.10 Cr')
  })

  it('describes relative time', () => {
    const now = new Date('2026-01-10T12:00:00Z').getTime()
    expect(relative('2026-01-10T10:00:00Z', now)).toBe('2h ago')
    expect(relative('2026-01-13T12:00:00Z', now)).toBe('in 3d')
  })

  it('formats file sizes and enum labels', () => {
    expect(fileSize(2048)).toBe('2 KB')
    expect(fileSize(3 * 1024 * 1024)).toBe('3.0 MB')
    expect(titleCase('PENDING_APPROVAL')).toBe('Pending Approval')
  })
})
