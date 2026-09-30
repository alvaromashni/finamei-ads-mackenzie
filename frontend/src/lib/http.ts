const LOCAL_API_URL = 'http://localhost:8080/api/v1'
const PRODUCTION_API_URL = 'https://finamei-ads-mackenzie.onrender.com/api/v1'

/**
 * `npm run dev` talks to the local backend; the production build (Vercel)
 * talks to the backend on Render. VITE_API_URL overrides both.
 */
const API_URL =
  import.meta.env.VITE_API_URL ??
  (import.meta.env.PROD ? PRODUCTION_API_URL : LOCAL_API_URL)

export class HttpError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, body: unknown) {
    super(`A requisição falhou com status ${status}.`)
    this.name = 'HttpError'
    this.status = status
    this.body = body
  }
}

export type ApiFetchOptions = RequestInit & {
  /** JWT do usuário autenticado; quando informado, envia o header Authorization. */
  token?: string | null
}

async function request(
  path: string,
  { token, ...init }: ApiFetchOptions,
): Promise<Response> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new HttpError(response.status, body)
  }

  return response
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  const response = await request(path, options)

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export type DownloadedFile = {
  blob: Blob
  /** Name sent by the server in Content-Disposition, when present. */
  filename: string | null
}

function getFilename(contentDisposition: string | null) {
  if (!contentDisposition) return null
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition)
  if (encoded) return decodeURIComponent(encoded[1])
  const plain = /filename="?([^";]+)"?/i.exec(contentDisposition)
  return plain ? plain[1] : null
}

/** Fetches a binary file (e.g. an exported report). */
export async function apiDownload(
  path: string,
  options: ApiFetchOptions = {},
): Promise<DownloadedFile> {
  const response = await request(path, options)
  return {
    blob: await response.blob(),
    filename: getFilename(response.headers.get('Content-Disposition')),
  }
}
