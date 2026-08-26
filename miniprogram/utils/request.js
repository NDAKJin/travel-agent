const { BASE_URL } = require("./config");
const { persistSession, clearSession, redirectToLogin } = require("./auth");

let refreshPromise = null;

function buildError(statusCode, data) {
  let message = `请求失败，状态码 ${statusCode}`;
  let code;

  if (typeof data === "string" && data) {
    message = data;
  } else if (data && data.message) {
    message = data.message;
    code = data.code;
  }

  const error = new Error(message);
  error.statusCode = statusCode;
  error.code = code;
  return error;
}

function buildSilentSessionError() {
  const error = new Error("");
  error.silent = true;
  error.code = "SESSION_EXPIRED";
  return error;
}

function baseRequest(path, options = {}) {
  return new Promise((resolve, reject) => {
    const requestId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const startedAt = Date.now();
    const method = path === "/api/auth/refresh" ? "POST" : (options.method || "GET");
    const app = getApp();
    const session = app.globalData.session;
    const headers = Object.assign(
      {
        "Content-Type": "application/json"
      },
      options.headers || {}
    );

    if (options.withAuth && session && session.token && session.token.accessToken) {
      headers.Authorization = `Bearer ${session.token.accessToken}`;
    }

    wx.request({
      url: `${BASE_URL}${path}`,
      method,
      data: options.data,
      header: headers,
      success(res) {
        const { statusCode, data } = res;
        console.log("[mini-api] response", { requestId, path, statusCode });
        if (statusCode >= 200 && statusCode < 300) {
          resolve(data);
          return;
        }

        reject(buildError(statusCode, data));
      },
      fail(error) {
        console.error("[mini-api] network failure", {
          requestId,
          path,
          durationMs: Date.now() - startedAt,
          error: error.errMsg
        });
        reject(new Error(error.errMsg || "网络请求失败"));
      },
      complete() {
        console.log("[mini-api] request completed", {
          requestId,
          method,
          path,
          durationMs: Date.now() - startedAt
        });
      }
    });
  });
}

function shouldRefresh(options, error) {
  return Boolean(
    options.withAuth &&
      error &&
      error.statusCode === 401 &&
      error.code === "AUTH_ERROR"
  );
}

function refreshSession() {
  if (refreshPromise) {
    return refreshPromise;
  }

  const app = getApp();
  const session = app.globalData.session;
  const refreshToken = session && session.token && session.token.refreshToken;

  if (!refreshToken) {
    clearSession();
    redirectToLogin();
    return Promise.reject(buildSilentSessionError());
  }

  refreshPromise = baseRequest("/api/auth/refresh", {
    method: "POST",
    data: { refreshToken }
  })
    .then((nextSession) => {
      persistSession(nextSession);
      return nextSession;
    })
    .catch(() => {
      clearSession();
      redirectToLogin();
      throw buildSilentSessionError();
    })
    .finally(() => {
      refreshPromise = null;
    });

  return refreshPromise;
}

function request(path, options = {}) {
  return baseRequest(path, options).catch((error) => {
    if (!shouldRefresh(options, error)) {
      throw error;
    }

    return refreshSession().then(() => baseRequest(path, options));
  });
}

module.exports = {
  request
};
