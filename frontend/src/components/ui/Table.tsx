import type { ReactNode } from 'react';

export interface TableContainerProps {
  children: ReactNode;
  className?: string;
  overflow?: 'auto' | 'x' | 'y';
}

export interface TableProps {
  children: ReactNode;
  className?: string;
  fullWidth?: boolean;
}

export interface TableHeaderProps {
  children: ReactNode;
  className?: string;
}

export interface TableBodyProps {
  children: ReactNode;
  className?: string;
}

export interface TableRowProps {
  children: ReactNode;
  className?: string;
  hover?: boolean;
}

export interface TableHeadProps {
  children: ReactNode;
  className?: string;
  numeric?: boolean;
}

export interface TableCellProps {
  children: ReactNode;
  className?: string;
  numeric?: boolean;
  padded?: boolean;
}

export interface TableEmptyRowProps {
  message?: string;
  className?: string;
  colSpan?: number;
}

export function TableContainer({
  children,
  className = '',
  overflow = 'auto',
}: TableContainerProps) {
  return (
    <div
      className={`relative overflow-${overflow} border rounded-lg border-c-border ${className}`}
    >
      {children}
    </div>
  );
}

export function Table({ children, className = '', fullWidth = false }: TableProps) {
  return (
    <table
      className={`min-w-full divide-y divide-c-border ${className} ${
        fullWidth ? 'w-full' : ''
      }`}
    >
      {children}
    </table>
  );
}

export function TableHeader({ children, className = '' }: TableHeaderProps) {
  return <thead className={className}>{children}</thead>;
}

export function TableBody({ children, className = '' }: TableBodyProps) {
  return <tbody className={className}>{children}</tbody>;
}

export function TableRow({
  children,
  className = '',
  hover = false,
}: TableRowProps) {
  return (
    <tr
      className={`bg-c-surface border-b border-c-border transition-colors hover:bg-c-surface-sunken ${hover ? 'hover:bg-c-surface-sunken' : ''} ${className}`}
    >
      {children}
    </tr>
  );
}

export function TableHead({
  children,
  className = '',
  numeric = false,
}: TableHeadProps) {
  return (
    <th
      scope="col"
      className={`h-11 text-left font-label-md text-label-md text-c-text-muted border-b border-c-border ${numeric ? 'text-right' : ''} ${className}`}
    >
      {children}
    </th>
  );
}

export function TableCell({
  children,
  className = '',
  numeric = false,
  padded = true,
}: TableCellProps) {
  return (
    <td
      className={`border-b border-c-border py-3.5 ${padded ? 'px-4' : ''} ${numeric ? 'text-right' : ''} ${className}`}
    >
      {children}
    </td>
  );
}

export function TableEmptyRow({
  message = 'No data available.',
  className = '',
  colSpan = 99,
}: TableEmptyRowProps) {
  return (
    <tr>
      <td
        className={`h-32 text-center text-c-text-muted border-b border-c-border ${className}`}
        colSpan={colSpan}
      >
        {message}
      </td>
    </tr>
  );
}