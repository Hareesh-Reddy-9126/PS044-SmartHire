import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AuthLayout, Layout, PublicLayout } from '../components/layout/Layout';
import { Spinner } from '../components/ui/Spinner';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { LandingPage } from '../pages/public/LandingPage';
import { JobBrowsePage } from '../pages/public/JobBrowsePage';
import { JobDetailPage } from '../pages/public/JobDetailPage';
import { CandidateApplicationsPage } from '../pages/candidate/CandidateApplicationsPage';
import { ApplicationDetailPage } from '../pages/candidate/ApplicationDetailPage';
import { DocumentsPage } from '../pages/candidate/DocumentsPage';
import { NotificationDetailPage } from '../pages/candidate/NotificationDetailPage';
import { NotificationsPage } from '../pages/candidate/NotificationsPage';
import { ProgressDetailPage } from '../pages/candidate/ProgressDetailPage';
import { ProgressPage } from '../pages/candidate/ProgressPage';
import { ApplicationSubmitPage } from '../pages/candidate/ApplicationSubmitPage';
import { CandidateProfilePage } from '../pages/candidate/CandidateProfilePage';
import { RecruiterJobsPage } from '../pages/recruiter/RecruiterJobsPage';
import { JobCreatePage } from '../pages/recruiter/JobCreatePage';
import { JobEditPage } from '../pages/recruiter/JobEditPage';
import { RecruiterJobDetailPage } from '../pages/recruiter/RecruiterJobDetailPage';
import { RecruiterApplicationLookupPage } from '../pages/recruiter/RecruiterApplicationLookupPage';
import { RecruiterRecruitmentsPage } from '../pages/recruiter/RecruiterRecruitmentsPage';
import { RecruiterRecruitmentDetailPage } from '../pages/recruiter/RecruiterRecruitmentDetailPage';
import { RecruiterProfilePage } from '../pages/recruiter/RecruiterProfilePage';
import { AdminOverviewPage } from '../pages/admin/AdminOverviewPage';
import { AdminRecruitmentsPage } from '../pages/admin/AdminRecruitmentsPage';
import { AdminRecruitmentDetailPage } from '../pages/admin/AdminRecruitmentDetailPage';
import { AdminLookupPage } from '../pages/admin/AdminLookupPage';
import { AdminProfilePage } from '../pages/admin/AdminProfilePage';
import { CompanyOnboardingPage } from '../pages/recruiter/CompanyOnboardingPage';
import { CompanyDashboardPage } from '../pages/recruiter/CompanyDashboardPage';
import { CompanyRecruitmentsPage } from '../pages/recruiter/CompanyRecruitmentsPage';
import { CompanyMembersPage } from '../pages/recruiter/CompanyMembersPage';
import { CompanyJobCreatePage } from '../pages/recruiter/CompanyJobCreatePage';
import {
  ForbiddenPage,
  NotFoundPage,
  OfflinePage,
  SessionExpiredPage,
  UnexpectedErrorPage,
} from '../pages/shared/ErrorPages';
import { roleHome } from '../utils/roles';

export const ProtectedRoute = ({
  children,
  allowedRoles,
}: {
  children: React.ReactNode;
  allowedRoles?: string[];
}) => {
  const { user, isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-c-bg">
        <Spinner className="h-7 w-7 text-c-primary-700" label="Checking your session" />
      </div>
    );
  }

  if (!isAuthenticated) {
    const returnPath = `${window.location.pathname}${window.location.search}`;
    return <Navigate to={`/login?next=${encodeURIComponent(returnPath)}`} replace />;
  }

  if (allowedRoles && !allowedRoles.some(role => user?.roles.includes(role))) {
    return <Navigate to="/403" replace />;
  }

  return <>{children}</>;
};

const CandidateRoute = ({ children }: { children: React.ReactNode }) => (
  <ProtectedRoute allowedRoles={['CANDIDATE']}>
    <Layout>{children}</Layout>
  </ProtectedRoute>
);

const RecruiterRoute = ({ children }: { children: React.ReactNode }) => (
  <ProtectedRoute allowedRoles={['RECRUITER']}>
    <Layout>{children}</Layout>
  </ProtectedRoute>
);

const AdminRoute = ({ children }: { children: React.ReactNode }) => (
  <ProtectedRoute allowedRoles={['ADMIN']}>
    <Layout>{children}</Layout>
  </ProtectedRoute>
);

function WorkspaceRedirect() {
  const { user, isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-c-bg">
        <Spinner className="h-7 w-7 text-c-primary-700" label="Loading workspace" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Navigate to={roleHome(user?.roles)} replace />;
}

export function AppRoutes() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/" element={<PublicLayout><LandingPage /></PublicLayout>} />
      <Route path="/jobs" element={<PublicLayout><JobBrowsePage /></PublicLayout>} />
      <Route path="/jobs/:jobId" element={<PublicLayout><JobDetailPage /></PublicLayout>} />
      <Route
        path="/jobs/:jobId/apply"
        element={
          <CandidateRoute>
            <ApplicationSubmitPage />
          </CandidateRoute>
        }
      />

      {/* Auth */}
      <Route path="/login" element={<AuthLayout><LoginPage /></AuthLayout>} />
      <Route path="/register" element={<AuthLayout><RegisterPage /></AuthLayout>} />
      <Route
        path="/session-expired"
        element={
          <AuthLayout>
            <SessionExpiredPage />
          </AuthLayout>
        }
      />

      {/* Errors */}
      <Route path="/403" element={<PublicLayout><ForbiddenPage /></PublicLayout>} />
      <Route path="/404" element={<PublicLayout><NotFoundPage /></PublicLayout>} />
      <Route path="/error" element={<PublicLayout><UnexpectedErrorPage /></PublicLayout>} />
      <Route path="/offline" element={<PublicLayout><OfflinePage /></PublicLayout>} />

      {/* Candidate */}
      <Route path="/candidate" element={<Navigate to="/candidate/applications" replace />} />
      <Route
        path="/candidate/applications"
        element={
          <CandidateRoute>
            <CandidateApplicationsPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/applications/:applicationId"
        element={
          <CandidateRoute>
            <ApplicationDetailPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/progress"
        element={
          <CandidateRoute>
            <ProgressPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/progress/:recruitmentId"
        element={
          <CandidateRoute>
            <ProgressDetailPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/documents"
        element={
          <CandidateRoute>
            <DocumentsPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/notifications"
        element={
          <CandidateRoute>
            <NotificationsPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/notifications/:notificationId"
        element={
          <CandidateRoute>
            <NotificationDetailPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/candidate/profile"
        element={
          <CandidateRoute>
            <CandidateProfilePage />
          </CandidateRoute>
        }
      />

      {/* Recruiter */}
      <Route path="/recruiter" element={<Navigate to="/recruiter/jobs" replace />} />
      <Route
        path="/recruiter/jobs"
        element={
          <RecruiterRoute>
            <RecruiterJobsPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/jobs/new"
        element={
          <RecruiterRoute>
            <JobCreatePage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/jobs/:jobId"
        element={
          <RecruiterRoute>
            <RecruiterJobDetailPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/jobs/:jobId/edit"
        element={
          <RecruiterRoute>
            <JobEditPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/applications"
        element={
          <RecruiterRoute>
            <RecruiterApplicationLookupPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/applications/:applicationId"
        element={
          <RecruiterRoute>
            <RecruiterApplicationLookupPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/recruitments"
        element={
          <RecruiterRoute>
            <RecruiterRecruitmentsPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/recruitments/:recruitmentId"
        element={
          <RecruiterRoute>
            <RecruiterRecruitmentDetailPage />
          </RecruiterRoute>
        }
      />
      <Route
        path="/recruiter/profile"
        element={
          <RecruiterRoute>
            <RecruiterProfilePage />
          </RecruiterRoute>
        }
      />

      {/* Company pages - accessible by company members */}
      <Route
        path="/company/register"
        element={
          <ProtectedRoute allowedRoles={['RECRUITER', 'ADMIN', 'CANDIDATE']}>
            <CompanyOnboardingPage />
          </ProtectedRoute>
        }
      />
      <Route path="/company/dashboard" element={<CompanyDashboardPage />} />
      <Route path="/company/recruitments" element={<CompanyRecruitmentsPage />} />
      <Route path="/company/members" element={<CompanyMembersPage />} />
      <Route path="/company/jobs/new" element={<CompanyJobCreatePage />} />


      {/* Admin */}
      <Route path="/admin" element={<Navigate to="/admin/overview" replace />} />
      <Route
        path="/admin/overview"
        element={
          <AdminRoute>
            <AdminOverviewPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/recruitments"
        element={
          <AdminRoute>
            <AdminRecruitmentsPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/recruitments/:recruitmentId"
        element={
          <AdminRoute>
            <AdminRecruitmentDetailPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/lookup"
        element={
          <AdminRoute>
            <AdminLookupPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/profile"
        element={
          <AdminRoute>
            <AdminProfilePage />
          </AdminRoute>
        }
      />

      <Route path="/workspace" element={<WorkspaceRedirect />} />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}
