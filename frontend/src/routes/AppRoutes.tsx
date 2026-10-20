import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Layout, PublicLayout, AuthLayout } from '../components/layout/Layout';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { JobBrowsePage } from '../pages/public/JobBrowsePage';

// Placeholder pages for unimplemented routes
const PlaceholderPage = ({ title, description }: { title: string; description: string }) => (
  <Layout>
    <div className="text-center py-12">
      <h1 className="font-headline-lg text-headline-lg text-on-surface mb-2">{title}</h1>
      <p className="font-body-md text-body-md text-c-text-muted">{description}</p>
      <div className="mt-4 p-4 bg-c-surface-sunken rounded-lg text-c-text-muted font-caption text-caption">
        This screen is not yet implemented in Phase 1.
      </div>
    </div>
  </Layout>
);

export const ProtectedRoute = ({ children, allowedRoles }: { children: React.ReactNode; allowedRoles?: string[] }) => {
  const { user, isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return (
      <Layout>
        <div className="min-h-[60vh] flex items-center justify-center">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary border-t-transparent" />
        </div>
      </Layout>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: window.location.pathname }} />;
  }

  if (allowedRoles && !allowedRoles.some(role => user?.roles.includes(role))) {
    return <Navigate to="/403" replace />;
  }

  return <>{children}</>;
};

export function AppRoutes() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/jobs" element={<PublicLayout><JobBrowsePage /></PublicLayout>} />
      <Route path="/jobs/:jobId" element={<PublicLayout><PlaceholderPage title="Job Detail" description="Job detail page" /></PublicLayout>} />
      <Route path="/jobs/:jobId/apply" element={<AuthLayout><PlaceholderPage title="Apply to Job" description="Application form" /></AuthLayout>} />
      
      {/* Auth routes */}
      <Route path="/login" element={<AuthLayout><LoginPage /></AuthLayout>} />
      <Route path="/register" element={<AuthLayout><RegisterPage /></AuthLayout>} />
      <Route path="/session-expired" element={<AuthLayout><PlaceholderPage title="Session Expired" description="Session expired interstitial" /></AuthLayout>} />
      
      {/* Error routes - public layout but with error styling */}
      <Route path="/403" element={<PublicLayout><PlaceholderPage title="Forbidden" description="Access denied" /></PublicLayout>} />
      <Route path="/404" element={<PublicLayout><PlaceholderPage title="Not Found" description="Page not found" /></PublicLayout>} />
      <Route path="/error" element={<PublicLayout><PlaceholderPage title="Error" description="Unexpected error" /></PublicLayout>} />
      <Route path="/offline" element={<PublicLayout><PlaceholderPage title="Offline" description="Network unreachable" /></PublicLayout>} />

      {/* Candidate routes */}
      <Route 
        path="/candidate/*" 
        element={
          <ProtectedRoute allowedRoles={['CANDIDATE']}>
            <PlaceholderPage title="Candidate Area" description="Candidate dashboard area" />
          </ProtectedRoute>
        } 
      />
      <Route path="/candidate/applications" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="My Applications" description="Candidate applications list" /></ProtectedRoute>} />
      <Route path="/candidate/applications/:id" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="Application Detail" description="Application detail view" /></ProtectedRoute>} />
      <Route path="/candidate/progress" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="My Progress" description="Recruitment progress" /></ProtectedRoute>} />
      <Route path="/candidate/progress/:id" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="Progress Detail" description="Recruitment progress detail" /></ProtectedRoute>} />
      <Route path="/candidate/documents" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="My Documents" description="Document management" /></ProtectedRoute>} />
      <Route path="/candidate/notifications" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="Notifications" description="Notification list" /></ProtectedRoute>} />
      <Route path="/candidate/profile" element={<ProtectedRoute allowedRoles={['CANDIDATE']}><PlaceholderPage title="Profile" description="Read-only profile" /></ProtectedRoute>} />

      {/* Recruiter routes */}
      <Route 
        path="/recruiter/*" 
        element={
          <ProtectedRoute allowedRoles={['RECRUITER']}>
            <PlaceholderPage title="Recruiter Area" description="Recruiter dashboard area" />
          </ProtectedRoute>
        } 
      />
      <Route path="/recruiter/jobs" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Job Postings" description="Recruiter job postings" /></ProtectedRoute>} />
      <Route path="/recruiter/jobs/new" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Create Job" description="Create new job posting" /></ProtectedRoute>} />
      <Route path="/recruiter/jobs/:jobId" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Job Detail" description="Recruiter job detail" /></ProtectedRoute>} />
      <Route path="/recruiter/jobs/:jobId/edit" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Edit Job" description="Edit job posting" /></ProtectedRoute>} />
      <Route path="/recruiter/applications" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Application Lookup" description="Lookup application by ID" /></ProtectedRoute>} />
      <Route path="/recruiter/applications/:id" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Application Review" description="Review application" /></ProtectedRoute>} />
      <Route path="/recruiter/recruitments" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Recruitments" description="Recruitment list" /></ProtectedRoute>} />
      <Route path="/recruiter/recruitments/:id" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Recruitment Workflow" description="Recruitment workflow detail" /></ProtectedRoute>} />
      <Route path="/recruiter/profile" element={<ProtectedRoute allowedRoles={['RECRUITER']}><PlaceholderPage title="Profile" description="Read-only profile" /></ProtectedRoute>} />

      {/* Admin routes */}
      <Route 
        path="/admin/*" 
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <PlaceholderPage title="Admin Area" description="Admin dashboard area" />
          </ProtectedRoute>
        } 
      />
      <Route path="/admin/overview" element={<ProtectedRoute allowedRoles={['ADMIN']}><PlaceholderPage title="Admin Overview" description="Admin overview" /></ProtectedRoute>} />
      <Route path="/admin/recruitments" element={<ProtectedRoute allowedRoles={['ADMIN']}><PlaceholderPage title="All Recruitments" description="All recruitments list" /></ProtectedRoute>} />
      <Route path="/admin/lookup" element={<ProtectedRoute allowedRoles={['ADMIN']}><PlaceholderPage title="Record Lookup" description="Lookup by ID" /></ProtectedRoute>} />
      <Route path="/admin/profile" element={<ProtectedRoute allowedRoles={['ADMIN']}><PlaceholderPage title="Profile" description="Read-only profile" /></ProtectedRoute>} />

      {/* Redirect root to jobs */}
      <Route path="/" element={<Navigate to="/jobs" replace />} />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}