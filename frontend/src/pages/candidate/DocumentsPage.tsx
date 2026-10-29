import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { documentsApi } from '../../api/client';
import type { DocumentResponse, DocumentType } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { ConfirmDialog } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { Select } from '../../components/ui/Select';
import { SkeletonCards } from '../../components/ui/Skeleton';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../utils/errors';
import { formatDate, formatFileSize, humanise } from '../../utils/format';

const PAGE_SIZE = 10;
const MAX_FILE_BYTES = 10 * 1024 * 1024;

const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
  { value: 'RESUME', label: 'Résumé' },
  { value: 'COVER_LETTER', label: 'Cover letter' },
  { value: 'PORTFOLIO', label: 'Portfolio' },
  { value: 'CERTIFICATE', label: 'Certificate' },
  { value: 'OTHER', label: 'Other' },
];

/** MIME types document-service accepts, plus the extension check for files whose type is blank. */
const ALLOWED_MIME_TYPES = new Set([
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
]);
const ALLOWED_EXTENSIONS = /\.(pdf|doc|docx)$/i;

/**
 * The candidate's document library.
 *
 * Files are stored by document-service, not linked into applications: an
 * application carries a résumé URL, so this screen manages the candidate's own
 * uploads (download, remove) rather than acting as an application attachment.
 */
export function DocumentsPage() {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);

  const [documentType, setDocumentType] = useState<DocumentType>('RESUME');
  const [file, setFile] = useState<File | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  const [pendingDelete, setPendingDelete] = useState<DocumentResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await documentsApi.list(page, PAGE_SIZE);
      setDocuments(result.content ?? []);
      setTotalElements(result.totalElements ?? 0);
      setTotalPages(result.totalPages ?? 0);
    } catch (caught) {
      setDocuments([]);
      setError(errorMessage(caught, "We couldn't load your documents."));
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleUpload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setUploadError(null);

    if (!file) {
      setUploadError('Choose a file to upload.');
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setUploadError('Files must be 10 MB or smaller.');
      return;
    }
    if (!ALLOWED_MIME_TYPES.has(file.type) && !ALLOWED_EXTENSIONS.test(file.name)) {
      setUploadError('Upload a PDF, Word (.doc) or Word (.docx) file.');
      return;
    }

    setIsUploading(true);
    try {
      await documentsApi.upload({ documentType, file });
      showToast('Document uploaded.', 'success');
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      if (page !== 0) setPage(0);
      else await load();
    } catch (caught) {
      setUploadError(errorMessage(caught, "We couldn't upload that file."));
    } finally {
      setIsUploading(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;

    const deletedId = pendingDelete.documentId;
    setIsDeleting(true);
    try {
      await documentsApi.delete(deletedId);
      showToast('Document deleted.', 'success');
      setPendingDelete(null);

      // Remove the row locally; stepping back a page refetches instead.
      setDocuments(current => current.filter(doc => doc.documentId !== deletedId));
      setTotalElements(current => Math.max(current - 1, 0));

      if (documents.length === 1 && page > 0) {
        setPage(current => current - 1);
      }
    } catch (caught) {
      showToast(errorMessage(caught, "We couldn't delete that document."), 'error');
    } finally {
      setIsDeleting(false);
    }
  };

  const download = async (doc: DocumentResponse) => {
    try {
      const blob = await documentsApi.getContent(doc.documentId);
      const url = URL.createObjectURL(blob);
      const anchor = createDownloadAnchor(url, doc.fileName || 'document');
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (caught) {
      showToast(errorMessage(caught, "We couldn't download that document."), 'error');
    }
  };

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Candidate workspace"
        title="My documents"
        description="Résumés, cover letters and certificates you can reuse when applying."
      />

      <Card padding="lg">
        <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Upload a document</h2>

        {uploadError && (
          <div className="mb-4">
            <Alert tone="danger" title="Upload failed">
              {uploadError}
            </Alert>
          </div>
        )}

        <form onSubmit={handleUpload} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2 sm:items-end">
            <Select
              id="documentType"
              label="Document type"
              value={documentType}
              onChange={event => setDocumentType(event.target.value as DocumentType)}
              options={DOCUMENT_TYPES}
            />

            <div className="flex w-full flex-col">
              <label htmlFor="documentFile" className="label label-required">
                File
              </label>
              <input
                ref={fileInputRef}
                id="documentFile"
                type="file"
                accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={event => setFile(event.target.files?.[0] ?? null)}
                className="input file:mr-3 file:rounded-md file:border-0 file:bg-c-surface-sunken file:px-3 file:py-1.5 file:font-label-md file:text-label-md file:text-on-surface"
              />
              <p className="helper-text">PDF, DOC or DOCX, up to 10 MB.</p>
            </div>
          </div>

          <Button type="submit" isLoading={isUploading}>
            Upload document
          </Button>
        </form>
      </Card>

      {error && (
        <Alert
          tone="danger"
          title="Documents unavailable"
          action={
            <Button size="sm" variant="secondary" onClick={() => void load()}>
              Try again
            </Button>
          }
        >
          {error}
        </Alert>
      )}

      {isLoading ? (
        <SkeletonCards count={3} label="Loading documents" />
      ) : error ? null : documents.length === 0 ? (
        <EmptyState
          icon="folder_open"
          title="No documents yet"
          message="Upload a résumé so it is ready the next time you apply."
        />
      ) : (
        <>
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {documents.map(document => (
              <li key={document.documentId}>
                <Card padding="lg" className="flex h-full flex-col justify-between">
                  <div className="min-w-0">
                    <div className="mb-3 flex items-start justify-between gap-2">
                      <span className="font-label-md text-label-md text-c-primary-700">
                        {humanise(document.documentType)}
                      </span>
                      <span className="font-code-sm text-code-sm text-c-text-subtle tabular">
                        #{document.documentId.slice(0, 8)}
                      </span>
                    </div>

                    <h3 className="mb-2 break-words font-headline-sm text-headline-sm text-c-text">
                      {document.fileName || 'Untitled document'}
                    </h3>
                    <p className="font-body-sm text-body-sm text-c-text-muted">
                      {formatFileSize(document.fileSize)}
                    </p>
                    <p className="font-caption text-caption text-c-text-subtle">
                      Uploaded {formatDate(document.createdAt)}
                    </p>
                  </div>

                  <div className="mt-5 flex flex-wrap gap-2">
                    <Button variant="secondary" size="sm" onClick={() => void download(document)}>
                      Download
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setPendingDelete(document)}>
                      Delete
                    </Button>
                  </div>
                </Card>
              </li>
            ))}
          </ul>

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}

      {pendingDelete && (
        <ConfirmDialog
          title="Delete this document?"
          message={`"${pendingDelete.fileName}" will be removed permanently. Applications that already reference a résumé link are not affected.`}
          confirmLabel="Delete"
          onConfirm={() => void confirmDelete()}
          onCancel={() => setPendingDelete(null)}
          confirmLoading={isDeleting}
        />
      )}
    </div>
  );
}

/** Creates and appends the temporary anchor used to save a blob. */
function createDownloadAnchor(url: string, fileName: string): HTMLAnchorElement {
  const anchor = window.document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  window.document.body.appendChild(anchor);
  return anchor;
}
