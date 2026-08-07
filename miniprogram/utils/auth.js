const { getSessionStorage, setSessionStorage, clearSessionStorage } = require("./storage");

function redirectToLogin() {
  wx.reLaunch({
    url: "/pages/login/login"
  });
}

function restoreSession() {
  return getSessionStorage();
}

function persistSession(session) {
  setSessionStorage(session);
  getApp().globalData.session = session;
}

function clearSession() {
  clearSessionStorage();
  const app = getApp();
  if (app && app.globalData) {
    app.globalData.session = null;
    app.globalData.activeSessionId = null;
  }
}

function requireSession() {
  const app = getApp();
  const session = app.globalData.session || restoreSession();
  if (!session) {
    redirectToLogin();
    return null;
  }
  if (!app.globalData.session) {
    app.globalData.session = session;
  }
  return session;
}

module.exports = {
  restoreSession,
  persistSession,
  clearSession,
  requireSession,
  redirectToLogin
};
