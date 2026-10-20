package com.smarthire.job.service;

import com.smarthire.common.audit.AuditService;
import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.infra.JobRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Job aggregate (Inc 2).
 *
 * <p>This is the object-level authorization layer. The filter chain has already established
 * <em>that</em> the caller is authenticated and method security has established <em>which role</em>
 * they hold; here we decide whether this particular organization may see or change this particular
 * job. {@code callerOrgId} always originates from the verified JWT {@code orgId} claim — never from
 * a body, query parameter or header — and is compared against the stored {@code owner_org_id}.
 *
 * <p>Visibility (decision 5) is one rule applied identically to listing and detail: a job is
 * visible when it is OPEN, or when the caller is a recruiter of the owning organization. A DRAFT or
 * CLOSED job is therefore invisible to the public, to candidates, and to recruiters of other
 * organizations — and reported as {@link JobNotFoundException} rather than a denial, so identifiers
 * cannot be probed.
 */
@Service
public class JobService {

  /** Upper bound on a LIKE filter, so a pathological pattern cannot be pushed into the database. */
  private static final int MAX_FILTER_LENGTH = 255;

  private final JobRepository jobRepository;

  public JobService(JobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  /**
   * Creates a job in DRAFT owned by the caller's organization. A recruiter whose token carries no
   * usable {@code orgId} is refused rather than defaulted to some organization.
   */
  @Transactional
  public Job create(String companyName, String role, String location, UUID ownerOrgId) {
    requireOrg(ownerOrgId);
    Job job = Job.createDraft(UUID.randomUUID(), companyName, role, location, ownerOrgId);
    Job saved = jobRepository.save(job);
    AuditService.job(
        AuditService.JobEvent.CREATE,
        ownerOrgId,
        saved.getJobId(),
        AuditService.Outcome.SUCCESS,
        null);
    return saved;
  }

  /** Edits a job's descriptive fields. Ownership and status are not editable through this path. */
  @Transactional
  public Job update(
      UUID jobId, String companyName, String role, String location, UUID callerOrgId) {
    Job job = loadOwned(jobId, callerOrgId);
    job.updateDetails(companyName, role, location);
    Job saved = jobRepository.save(job);
    AuditService.job(
        AuditService.JobEvent.UPDATE, callerOrgId, jobId, AuditService.Outcome.SUCCESS, null);
    return saved;
  }

  /** {@code DRAFT → OPEN} on a job owned by the caller's organization. */
  @Transactional
  public Job open(UUID jobId, UUID callerOrgId) {
    Job job = loadOwned(jobId, callerOrgId);
    job.open();
    Job saved = jobRepository.save(job);
    AuditService.job(
        AuditService.JobEvent.OPEN, callerOrgId, jobId, AuditService.Outcome.SUCCESS, null);
    return saved;
  }

  /** {@code OPEN → CLOSED} on a job owned by the caller's organization. */
  @Transactional
  public Job close(UUID jobId, UUID callerOrgId) {
    Job job = loadOwned(jobId, callerOrgId);
    job.close();
    Job saved = jobRepository.save(job);
    AuditService.job(
        AuditService.JobEvent.CLOSE, callerOrgId, jobId, AuditService.Outcome.SUCCESS, null);
    return saved;
  }

  /** {@code CLOSED → OPEN} on a job owned by the caller's organization. */
  @Transactional
  public Job reopen(UUID jobId, UUID callerOrgId) {
    Job job = loadOwned(jobId, callerOrgId);
    job.reopen();
    Job saved = jobRepository.save(job);
    AuditService.job(
        AuditService.JobEvent.REOPEN, callerOrgId, jobId, AuditService.Outcome.SUCCESS, null);
    return saved;
  }

  /**
   * Reads one job, subject to the visibility rule. A job the caller may not see is reported as
   * missing.
   */
  @Transactional(readOnly = true)
  public Job get(UUID jobId, UUID callerOrgId, boolean isRecruiter) {
    Job job = load(jobId);
    if (!job.isPubliclyVisible() && !(isRecruiter && job.isOwnedBy(callerOrgId))) {
      throw new JobNotFoundException("Job not found: " + jobId);
    }
    return job;
  }

  /**
   * Lists the jobs this caller may see, narrowed by the optional filters. Anonymous and candidate
   * callers see OPEN jobs only; a recruiter additionally sees their own organization's DRAFT and
   * CLOSED jobs, and still browses other organizations' OPEN postings.
   */
  @Transactional(readOnly = true)
  public Page<Job> list(JobQuery query, UUID callerOrgId, boolean isRecruiter, Pageable pageable) {
    List<Specification<Job>> specs = new ArrayList<>();
    specs.add(visibleTo(callerOrgId, isRecruiter));
    if (query.status() != null) {
      specs.add(hasStatus(query.status()));
    }
    if (hasText(query.location())) {
      specs.add(locationContains(query.location()));
    }
    if (hasText(query.q())) {
      specs.add(companyOrRoleContains(query.q()));
    }
    return jobRepository.findAll(Specification.allOf(specs), pageable);
  }

  // --- authorization helpers -------------------------------------------------------------------

  /** Loads a job the caller's organization owns, or fails with 404 / 403 as appropriate. */
  private Job loadOwned(UUID jobId, UUID callerOrgId) {
    requireOrg(callerOrgId);
    Job job = load(jobId);
    if (!job.isOwnedBy(callerOrgId)) {
      AuditService.authorizationFailure(
          "job", jobId, callerOrgId, "Organization does not own this job");
      throw new JobForbiddenException("This job belongs to another organization.");
    }
    return job;
  }

  private Job load(UUID jobId) {
    return jobRepository
        .findByJobId(jobId)
        .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
  }

  private static void requireOrg(UUID callerOrgId) {
    if (callerOrgId == null) {
      throw new JobForbiddenException("The access token carries no organization; access denied.");
    }
  }

  // --- specifications --------------------------------------------------------------------------

  private static Specification<Job> visibleTo(UUID callerOrgId, boolean isRecruiter) {
    return (root, query, cb) -> {
      Predicate open = cb.equal(root.get("status"), JobStatus.OPEN);
      if (!isRecruiter || callerOrgId == null) {
        return open;
      }
      return cb.or(open, cb.equal(root.get("ownerOrgId"), callerOrgId));
    };
  }

  private static Specification<Job> hasStatus(JobStatus status) {
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  private static Specification<Job> locationContains(String location) {
    String pattern = containsPattern(location);
    return (root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern, '\\');
  }

  private static Specification<Job> companyOrRoleContains(String term) {
    String pattern = containsPattern(term);
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("companyName")), pattern, '\\'),
            cb.like(cb.lower(root.get("role")), pattern, '\\'));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  /**
   * Builds a case-insensitive {@code %term%} pattern with the LIKE metacharacters escaped, so a
   * user supplying {@code %} or {@code _} matches those literal characters instead of widening the
   * search.
   */
  private static String containsPattern(String term) {
    String trimmed = term.trim();
    if (trimmed.length() > MAX_FILTER_LENGTH) {
      trimmed = trimmed.substring(0, MAX_FILTER_LENGTH);
    }
    String escaped =
        trimmed
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
