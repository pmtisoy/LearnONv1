export async function request(path, options = {}) {
  const { csrf, headers, ...fetchOptions } = options;
  const requestHeaders = new Headers(headers);

  if (csrf) {
    requestHeaders.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(path, {
    credentials: "include",
    ...fetchOptions,
    headers: requestHeaders,
  });
  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (!response.ok) {
    const error = new Error(
      body?.message ?? body?.detail ?? `Request failed (${response.status}).`,
    );
    error.status = response.status;
    throw error;
  }

  return body;
}

export async function getCsrfToken() {
  return request("/api/auth/csrf");
}
