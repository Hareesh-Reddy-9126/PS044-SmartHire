import React from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { RoleChip } from '../ui/RoleChip';
import { Button } from '../ui/Button';

const NAV_ITEMS = {
  CANDIDATE: [
    { path: '/jobs', label: 'Browse jobs', icon: 'search' },
    { path: '/candidate/applications', label: 'My applications', icon: 'assignment' },
    { path: '/candidate/progress', label: 'My progress', icon: 'timeline' },
    { path: '/candidate/documents', label: 'My documents', icon: 'folder' },
    { path: '/candidate/notifications', label: 'Notifications', icon: 'notifications' },
    { path: '/candidate/profile', label: 'Profile', icon: 'person' },
  ],
  RECRUITER: [
    { path: '/recruiter/jobs', label: 'Job postings', icon: 'work' },
    { path: '/recruiter/recruitments', label: 'Recruitments', icon: 'group_work' },
    { path: '/recruiter/applications', label: 'Look up application', icon: 'find_in_page' },
    { path: '/jobs', label: 'Browse all jobs', icon: 'public' },
    { path: '/recruiter/profile', label: 'Profile', icon: 'person' },
  ],
  ADMIN: [
    { path: '/admin/overview', label: 'Overview', icon: 'dashboard' },
    { path: '/admin/recruitments', label: 'All recruitments', icon: 'list_alt' },
    { path: '/admin/lookup', label: 'Record lookup', icon: 'find_in_page' },
    { path: '/admin/profile', label: 'Profile', icon: 'person' },
  ],
};

export function TopBar() {
  const { user, logout } = useAuth();

  if (!user) return null;

  return (
    <header className="fixed top-0 left-0 right-0 h-14 bg-c-surface border-b border-c-border z-40 flex items-center justify-between px-gutter">
      <div className="flex items-center gap-3">
        <button className="lg:hidden p-2 rounded-lg text-c-text-muted hover:text-on-surface hover:bg-c-surface-sunken transition-colors" aria-label="Toggle menu">
          <span className="material-symbols-outlined text-[24px]">menu</span>
        </button>
        <span className="font-headline-md text-headline-md text-c-primary-900 font-semibold">SmartHire</span>
      </div>
      <div className="flex items-center gap-4">
        <div className="hidden sm:flex items-center gap-2">
          {user.roles.map(role => (
            <RoleChip key={role} role={role as 'CANDIDATE' | 'RECRUITER' | 'ADMIN'} size="sm" />
          ))}
        </div>
        <span className="hidden sm:block font-body-md text-body-md text-c-text-muted max-w-[200px] truncate">
          {user.email}
        </span>
        <Button variant="secondary" size="sm" onClick={logout}>
          <span className="material-symbols-outlined text-[18px]">logout</span>
          <span>Sign out</span>
        </Button>
      </div>
    </header>
  );
}

export function Sidebar() {
  const { user } = useAuth();

  if (!user) return null;

  const primaryRole = user.roles[0] || 'CANDIDATE';
  const navItems = NAV_ITEMS[primaryRole as keyof typeof NAV_ITEMS] || [];

  return (
    <aside className="fixed top-14 left-0 bottom-0 w-64 bg-c-primary-900 text-c-bg lg:block hidden flex flex-col z-30">
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto" aria-label="Main navigation">
        {navItems.map(item => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-body-md font-medium transition-colors ${
                isActive
                  ? 'bg-c-primary-800 text-c-bg'
                  : 'text-c-primary-100 hover:bg-c-primary-800/50 hover:text-c-bg'
              }`
            }
          >
            <span className="material-symbols-outlined text-[20px] flex-shrink-0">{item.icon}</span>
            <span className="truncate">{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="p-4 border-t border-c-primary-800">
        <a
          href="http://localhost:8080/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 px-3 py-2 text-caption text-c-primary-200 hover:text-c-bg transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]">description</span>
          <span>API documentation</span>
        </a>
      </div>
    </aside>
  );
}

export function Layout({ children }: { children: React.ReactNode }) {
  const { isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-c-bg">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-c-bg">
      <TopBar />
      <Sidebar />
      <main className="pt-14 lg:pl-64 min-h-[calc(100vh-56px)]">
        <div className="max-w-[1200px] mx-auto px-gutter py-6 lg:py-8">
          {children}
        </div>
      </main>
    </div>
  );
}

export function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-c-bg">
      <header className="fixed top-0 left-0 right-0 h-14 bg-c-surface border-b border-c-border z-40 flex items-center justify-between px-gutter">
        <span className="font-headline-md text-headline-md text-c-primary-900 font-semibold">SmartHire</span>
        <div className="flex items-center gap-4">
          <Link to="/jobs" className="font-body-md text-body-md text-c-text-muted hover:text-on-surface transition-colors">
            Browse jobs
          </Link>
          <Link to="/login" className="btn btn-secondary btn-sm">
            Sign in
          </Link>
          <Link to="/register" className="btn btn-primary btn-sm">
            Create account
          </Link>
        </div>
      </header>
      <main className="pt-14 min-h-[calc(100vh-56px)]">
        <div className="max-w-[1120px] mx-auto px-gutter py-6 lg:py-8">
          {children}
        </div>
      </main>
    </div>
  );
}

export function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-c-bg flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-[400px]">
        {children}
      </div>
    </div>
  );
}