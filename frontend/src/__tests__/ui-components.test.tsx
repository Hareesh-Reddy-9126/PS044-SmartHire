import { describe, expect, it, vi } from 'vitest';
import type { ChangeEvent } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import {
  Breadcrumbs,
  Tabs,
  Badge,
  Select,
  TableContainer,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  TableEmptyRow,
} from '../components/ui';

describe('Breadcrumbs Component', () => {
  it('renders nothing when items array is empty', () => {
    const { container } = render(<Breadcrumbs items={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders breadcrumb items with accessible navigation and aria-current on the active page', () => {
    const items = [
      { label: 'Home', href: '/' },
      { label: 'Jobs', href: '/jobs' },
      { label: 'Senior Engineer' },
    ];

    render(
      <MemoryRouter>
        <Breadcrumbs items={items} />
      </MemoryRouter>
    );

    const nav = screen.getByRole('navigation', { name: 'Breadcrumb' });
    expect(nav).toBeInTheDocument();

    const homeLink = screen.getByRole('link', { name: 'Home' });
    expect(homeLink).toHaveAttribute('href', '/');

    const jobsLink = screen.getByRole('link', { name: 'Jobs' });
    expect(jobsLink).toHaveAttribute('href', '/jobs');

    const currentItem = screen.getByText('Senior Engineer');
    expect(currentItem).toHaveAttribute('aria-current', 'page');
  });
});

describe('Tabs Component', () => {
  const tabs = [
    { value: 'all', label: 'All Items', count: 12 },
    { value: 'active', label: 'Active Items', count: 5 },
    { value: 'disabled', label: 'Archived', disabled: true },
  ];

  it('renders tabs with role="tablist" and aria-selected state', () => {
    const handleChange = vi.fn();
    render(<Tabs items={tabs} value="all" onChange={handleChange} ariaLabel="Test tabs" />);

    const tablist = screen.getByRole('tablist', { name: 'Test tabs' });
    expect(tablist).toBeInTheDocument();

    const allTab = screen.getByRole('tab', { name: /All Items/ });
    expect(allTab).toHaveAttribute('aria-selected', 'true');
    expect(allTab).toHaveAttribute('tabIndex', '0');

    const activeTab = screen.getByRole('tab', { name: /Active Items/ });
    expect(activeTab).toHaveAttribute('aria-selected', 'false');
    expect(activeTab).toHaveAttribute('tabIndex', '-1');

    const disabledTab = screen.getByRole('tab', { name: /Archived/ });
    expect(disabledTab).toBeDisabled();
  });

  it('triggers onChange when clicking an inactive tab', () => {
    const handleChange = vi.fn();
    render(<Tabs items={tabs} value="all" onChange={handleChange} />);

    const activeTab = screen.getByRole('tab', { name: /Active Items/ });
    fireEvent.click(activeTab);

    expect(handleChange).toHaveBeenCalledWith('active');
  });

  it('supports arrow key navigation across tabs', () => {
    const handleChange = vi.fn();
    render(<Tabs items={tabs} value="all" onChange={handleChange} />);

    const allTab = screen.getByRole('tab', { name: /All Items/ });
    allTab.focus();

    // ArrowRight should move to 'active' tab
    fireEvent.keyDown(allTab, { key: 'ArrowRight' });
    expect(handleChange).toHaveBeenCalledWith('active');
  });
});

describe('Select Component', () => {
  const options = [
    { value: 'CANDIDATE', label: 'Candidate' },
    { value: 'RECRUITER', label: 'Recruiter' },
    { value: 'ADMIN', label: 'Admin' },
  ];

  it('opens a branded listbox and reports the clicked value', () => {
    const onChange = vi.fn();
    render(
      <Select
        label="Sign in as"
        value="CANDIDATE"
        options={options}
        onChange={onChange}
      />
    );

    const trigger = screen.getByRole('combobox', { name: 'Sign in as' });
    expect(trigger).toHaveTextContent('Candidate');

    fireEvent.click(trigger);
    expect(screen.getByRole('listbox')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('option', { name: 'Recruiter' }));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0].target.value).toBe('RECRUITER');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('supports arrow-key navigation and Enter selection', () => {
    const onChange = vi.fn();
    render(
      <Select
        label="Sign in as"
        value="CANDIDATE"
        options={options}
        onChange={onChange}
      />
    );

    const trigger = screen.getByRole('combobox', { name: 'Sign in as' });
    trigger.focus();
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.keyDown(trigger, { key: 'Enter' });

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0].target.value).toBe('RECRUITER');
  });

  it('keeps the hidden native select working for form and test compatibility', () => {
    let capturedValue = '';
    const onChange = vi.fn((event: ChangeEvent<HTMLSelectElement>) => {
      capturedValue = event.target.value;
    });
    const { container } = render(
      <Select
        id="role"
        label="Sign in as"
        value="CANDIDATE"
        options={options}
        onChange={onChange}
      />
    );

    const native = container.querySelector('select');
    expect(native).toHaveAttribute('id', 'role');
    expect(native).toHaveAttribute('hidden');

    fireEvent.change(native!, { target: { value: 'ADMIN' } });
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(capturedValue).toBe('ADMIN');
  });
});

describe('Badge Component', () => {
  it('renders children with default tone and size', () => {
    render(<Badge>Verified</Badge>);
    const badgeText = screen.getByText('Verified');
    expect(badgeText).toBeInTheDocument();
    const badge = badgeText.parentElement;
    expect(badge?.className).toContain('rounded-full');
  });

  it('renders with optional dot indicator and specific tone classes', () => {
    const { container } = render(
      <Badge tone="success" withDot size="sm">
        Active
      </Badge>
    );
    expect(screen.getByText('Active')).toBeInTheDocument();
    const dot = container.querySelector('span[aria-hidden="true"]');
    expect(dot).toBeInTheDocument();
    expect(dot?.className).toContain('rounded-full');
  });
});

describe('Table Components', () => {
  it('renders structured table elements with data and empty row states', () => {
    render(
      <TableContainer>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Header 1</TableHead>
              <TableHead>Header 2</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>Cell 1</TableCell>
              <TableCell>Cell 2</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </TableContainer>
    );

    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByText('Header 1')).toBeInTheDocument();
    expect(screen.getByText('Cell 1')).toBeInTheDocument();
  });

  it('renders TableEmptyRow with specified colSpan and message', () => {
    render(
      <Table>
        <TableBody>
          <TableEmptyRow message="No records found in database" colSpan={4} />
        </TableBody>
      </Table>
    );

    const emptyCell = screen.getByText('No records found in database');
    expect(emptyCell).toBeInTheDocument();
    expect(emptyCell.closest('td')).toHaveAttribute('colspan', '4');
  });
});
