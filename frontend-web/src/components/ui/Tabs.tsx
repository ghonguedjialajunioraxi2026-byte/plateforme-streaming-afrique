import { cn } from '../../utils/cn'

export interface TabItem {
  key: string
  label: string
}

interface TabsProps {
  items: TabItem[]
  activeKey: string
  onChange: (key: string) => void
  className?: string
}

export function Tabs({ items, activeKey, onChange, className }: TabsProps) {
  return (
    <div
      role="tablist"
      className={cn(
        'flex gap-1 rounded-lg bg-bg-surface p-1',
        className,
      )}
    >
      {items.map((item) => (
        <button
          key={item.key}
          role="tab"
          aria-selected={item.key === activeKey}
          onClick={() => onChange(item.key)}
          className={cn(
            'flex-1 rounded-md px-4 py-2 text-sm font-medium transition-colors',
            item.key === activeKey
              ? 'bg-primary-500 text-text-inverse'
              : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary',
          )}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}