const { request } = require("./request");

function loginWx(payload) {
  return request("/api/auth/wx/login", {
    method: "POST",
    data: payload
  });
}

function logout(refreshToken) {
  return request("/api/auth/logout", {
    method: "POST",
    data: { refreshToken }
  });
}

function refresh(refreshToken) {
  return request("/api/auth/refresh", {
    method: "POST",
    data: { refreshToken }
  });
}

function listSessions() {
  return request("/api/agent/sessions", {
    withAuth: true
  });
}

function createSession() {
  return request("/api/agent/sessions", {
    method: "POST",
    withAuth: true
  });
}

function getSession(sessionId) {
  return request(`/api/agent/sessions/${sessionId}`, {
    withAuth: true
  });
}

function deleteSession(sessionId) {
  return request(`/api/agent/sessions/${sessionId}`, {
    method: "DELETE",
    withAuth: true
  });
}

function chat(payload) {
  return request("/api/agent/chat", {
    method: "POST",
    withAuth: true,
    data: payload
  });
}

function nextNearbyPage(payload) {
  return request("/api/agent/nearby/next", {
    method: "POST",
    withAuth: true,
    data: payload
  });
}

module.exports = {
  loginWx,
  refresh,
  logout,
  createSession,
  listSessions,
  getSession,
  deleteSession,
  nextNearbyPage,
  chat
};
