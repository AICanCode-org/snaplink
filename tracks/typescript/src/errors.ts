// Domain errors that map 1:1 to RFC 9457 problem responses.
export const PROBLEM_BASE = "https://snaplink.dev/problems/";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly slug: string,
    readonly title: string,
    readonly detail?: string,
  ) {
    super(detail ?? title);
  }

  toProblem(): Record<string, unknown> {
    const body: Record<string, unknown> = {
      type: PROBLEM_BASE + this.slug,
      title: this.title,
      status: this.status,
    };
    if (this.detail) body.detail = this.detail;
    return body;
  }
}

export const validationError = (detail: string) =>
  new ApiError(400, "validation-error", "Validation failed", detail);
export const notFound = (code: string) =>
  new ApiError(404, "not-found", "Link not found", `No link with code '${code}'`);
export const expired = (code: string) =>
  new ApiError(410, "expired", "Link expired", `Link '${code}' has expired`);
export const aliasTaken = (alias: string) =>
  new ApiError(409, "alias-taken", "Alias already taken", `The alias '${alias}' is already in use`);

/** Thrown by a repository when the unique constraint on links.code is violated. */
export class DuplicateCodeError extends Error {}
