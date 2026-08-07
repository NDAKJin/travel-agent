const { chat, createSession, getSession, listSessions, deleteSession, nextNearbyPage, logout: logoutApi } = require("../../utils/api");
const { requireSession, clearSession } = require("../../utils/auth");
const { markdownToHtml } = require("../../utils/markdown");

function createGreetingMessage() {
  const text = "你好，我是你的 AI 旅行助手。告诉我出发地、目的地、天数、预算和偏好，我会帮你生成可执行行程。";
  return {
    id: `assistant-${Date.now()}`,
    role: "assistant",
    text,
    html: markdownToHtml(text),
    meta: ""
  };
}

function createMessage(role, text, id, extra) {
  const content = text || "";
  return {
    id: id || `${role}-${Date.now()}`,
    role,
    text: content,
    html: role === "assistant" ? markdownToHtml(content) : "",
    meta: "",
    ...(extra || {})
  };
}

function isSilentError(error) {
  return Boolean(error && error.silent);
}

function requestCurrentLocationPermission() {
  return new Promise(resolve => {
    let settled = false;
    const finish = location => {
      if (settled) return;
      settled = true;
      resolve(location);
    };

    const getLocation = () => {
      wx.getLocation({
        type: "gcj02",
        success(result) {
          finish({ latitude: result.latitude, longitude: result.longitude });
        },
        fail() {
          finish(null);
        }
      });
    };

    const authorizeFromUserTap = () => {
      wx.authorize({
        scope: "scope.userLocation",
        success: getLocation,
        fail: () => {
          wx.openSetting({
            success(setting) {
              if (setting.authSetting && setting.authSetting["scope.userLocation"]) {
                getLocation();
              } else {
                finish(null);
              }
            },
            fail() {
              finish(null);
            }
          });
        }
      });
    };

    wx.getSetting({
      success(setting) {
        if (setting.authSetting && setting.authSetting["scope.userLocation"]) {
          getLocation();
          return;
        }
        wx.showModal({
          title: "需要位置权限",
          content: "查找附近景点需要获取当前位置，点击确定后授权。",
          confirmText: "授权当前位置",
          cancelText: "取消",
          success(result) {
            if (result.confirm) authorizeFromUserTap();
            else finish(null);
          },
          fail() {
            finish(null);
          }
        });
      },
      fail() {
        finish(null);
      }
    });

    setTimeout(() => finish(null), 15000);
  });
}

function refreshCurrentLocation() {
  return new Promise(resolve => {
    wx.getLocation({
      type: "gcj02",
      success(result) {
        resolve({ latitude: result.latitude, longitude: result.longitude });
      },
      fail() {
        resolve(null);
      }
    });
  });
}

Page({
  data: {
    messages: [],
    bottomMessageId: "",
    messageInput: "",
    sessionId: "",
    activeHistoryId: "",
    sidebarCollapsed: true,
    loading: false,
    errorMessage: "",
    history: [],
    historyLoading: false,
    historyError: "",
    location: null
  },

  async onLoad(options) {
    if (!requireSession()) {
      return;
    }

    this.resetConversation();
    const app = getApp();
    this.setData({
      location: app.globalData.currentLocation || null
    });

    if (options.sessionId) {
      await this.loadHistorySession(options.sessionId);
      return;
    }

    const history = await this.loadHistory();
    if (history && history.length > 0) {
      await this.loadHistorySession(history[0].sessionId);
    } else {
      await this.ensureSession();
    }

    if (options.prompt) {
      const prompt = decodeURIComponent(options.prompt);
      this.setData({
        messageInput: prompt
      });
      await this.submitMessage(prompt);
    }
  },

  onShow() {
    if (!requireSession()) {
      return;
    }

    void this.loadHistory();
  },

  loadLocation() {
    wx.getLocation({
      type: "gcj02",
      success: (result) => {
        this.setData({
          location: {
            latitude: result.latitude,
            longitude: result.longitude
          }
        });
      },
      fail: () => {
        this.setData({ location: null });
      }
    });
  },

  handleInput(event) {
    this.setData({
      messageInput: event.detail.value
    });
  },

  toggleSidebar() {
    this.setData({
      sidebarCollapsed: !this.data.sidebarCollapsed
    });
  },

  logoutTap() {
    wx.showModal({
      title: "退出登录",
      content: "确定要退出当前账号吗？",
      confirmColor: "#d94841",
      success: async (result) => {
        if (!result.confirm) {
          return;
        }

        const session = requireSession();
        const refreshToken = session && session.token ? session.token.refreshToken : "";
        clearSession();
        getApp().globalData.currentLocation = null;
        wx.reLaunch({ url: "/pages/login/login" });
        if (refreshToken) {
          logoutApi(refreshToken).catch(() => {
            // 本地登录态已经清理，服务端退出失败不阻塞页面跳转。
          });
        }
      }
    });
  },

  resetConversation() {
    const greetingMessage = createGreetingMessage();
    this.setData({
      sessionId: "",
      activeHistoryId: "",
      bottomMessageId: greetingMessage.id,
      messageInput: "",
      errorMessage: "",
      messages: [greetingMessage]
    });
  },

  async ensureSession() {
    if (this.data.sessionId) {
      return this.data.sessionId;
    }

    try {
      const session = await createSession();
      this.setData({
        sessionId: session.sessionId,
        activeHistoryId: session.sessionId
      });
      await this.loadHistory();
      return session.sessionId;
    } catch (error) {
      if (isSilentError(error)) {
        return "";
      }
      this.setData({
        errorMessage: error.message || "创建会话失败"
      });
      return "";
    }
  },

  async loadHistory() {
    this.setData({
      historyLoading: true,
      historyError: ""
    });

    try {
      const history = await listSessions();
      this.setData({
        history
      });
      return history;
    } catch (error) {
      if (isSilentError(error)) {
        return [];
      }
      this.setData({
        historyError: error.message || "加载会话失败"
      });
      return [];
    } finally {
      this.setData({
        historyLoading: false
      });
    }
  },

  async loadHistorySession(sessionId) {
    this.setData({
      loading: true,
      errorMessage: ""
    });

    try {
      const detail = await getSession(sessionId);
      const messages = detail.messages.map((message, index) => createMessage(
        message.role,
        message.content,
        `${detail.sessionId}-${index}`
      ));
      const fallbackGreetingMessage = createGreetingMessage();

      this.setData({
        sessionId: detail.sessionId,
        activeHistoryId: detail.sessionId,
        messages: messages.length > 0 ? messages : [fallbackGreetingMessage],
        bottomMessageId: messages.length > 0 ? messages[messages.length - 1].id : fallbackGreetingMessage.id
      });
    } catch (error) {
      if (isSilentError(error)) {
        return;
      }
      this.setData({
        errorMessage: error.message || "打开会话失败"
      });
    } finally {
      this.setData({
        loading: false
      });
    }
  },

  async openHistory(event) {
    const sessionId = event.currentTarget.dataset.sessionId;
    if (!sessionId || this.data.loading) {
      return;
    }
    await this.loadHistorySession(sessionId);
  },

  deleteHistory(event) {
    const sessionId = event.currentTarget.dataset.sessionId;
    if (!sessionId || this.data.loading) {
      return;
    }

    const item = this.data.history.find(session => session.sessionId === sessionId);
    wx.showModal({
      title: "删除对话",
      content: `确定删除“${item && item.title ? item.title : "这条对话"}”吗？`,
      confirmColor: "#d94841",
      success: async (result) => {
        if (!result.confirm) {
          return;
        }

        try {
          await deleteSession(sessionId);
          const history = this.data.history.filter(session => session.sessionId !== sessionId);
          this.setData({ history, historyError: "" });
          if (this.data.activeHistoryId === sessionId) {
            if (history.length > 0) {
              await this.loadHistorySession(history[0].sessionId);
            } else {
              this.resetConversation();
              await this.ensureSession();
            }
          }
          wx.showToast({ title: "已删除", icon: "success" });
        } catch (error) {
          if (isSilentError(error)) {
            return;
          }
          this.setData({ historyError: error.message || "删除对话失败" });
        }
      }
    });
  },

  async startNewConversation() {
    if (this.data.loading) {
      return;
    }

    this.resetConversation();
    await this.ensureSession();
  },

  async sendTap() {
    await this.submitMessage(this.data.messageInput);
  },

  async loadNextPage(event) {
    const messageId = event.currentTarget.dataset.messageId;
    const index = this.data.messages.findIndex(message => message.id === messageId);
    const message = index >= 0 ? this.data.messages[index] : null;
    if (!message || !message.nearbySearch || !message.nearbySearch.hasMore || message.nearbyLoading) {
      return;
    }

    this.setData({ [`messages[${index}].nearbyLoading`]: true });
    try {
      const result = await nextNearbyPage({
        latitude: message.nearbySearch.latitude,
        longitude: message.nearbySearch.longitude,
        keyword: message.nearbySearch.keyword,
        radiusMeters: message.nearbySearch.radiusMeters,
        searchAfter: message.nearbySearch.searchAfter
      });
      this.setData({
        [`messages[${index}].nearbySearch`]: result,
        [`messages[${index}].nearbyLoading`]: false
      });
    } catch (error) {
      this.setData({ [`messages[${index}].nearbyLoading`]: false });
      if (!isSilentError(error)) this.setData({ errorMessage: error.message || "加载附近景点失败" });
    }
  },

  async submitMessage(text, permissionRetry = false) {
    const content = (text || "").trim();
    if (!content || (this.data.loading && !permissionRetry)) {
      return;
    }

    const nextSessionId = this.data.sessionId || await this.ensureSession();
    if (!nextSessionId) {
      return;
    }

    const nextMessages = permissionRetry
      ? this.data.messages
      : this.data.messages.concat([createMessage("user", content, `user-${Date.now()}`)]);

    this.setData({
      sessionId: nextSessionId,
      activeHistoryId: nextSessionId,
      bottomMessageId: nextMessages[nextMessages.length - 1].id,
      messageInput: "",
      errorMessage: "",
      loading: true,
      messages: nextMessages
    });

    try {
      const latestLocation = await refreshCurrentLocation();
      this.setData({ location: latestLocation });
      getApp().globalData.currentLocation = latestLocation;

      const response = await chat({
        message: content,
        sessionId: nextSessionId,
        location: latestLocation
      });

      if (response.locationPermissionRequired && !this.data.location) {
        const location = await requestCurrentLocationPermission();
        if (location) {
          getApp().globalData.currentLocation = location;
          this.setData({ location, loading: false });
          await this.submitMessage(content, true);
          return;
        }
      }

      const assistantMessage = createMessage("assistant", response.reply, `assistant-${Date.now()}`, {
        nearbySearch: response.nearbySearch || null,
        nearbyLoading: false
      });

      this.setData({
        sessionId: response.sessionId,
        activeHistoryId: response.sessionId,
        messages: nextMessages.concat([assistantMessage]),
        bottomMessageId: assistantMessage.id
      });
      void this.loadHistory();
    } catch (error) {
      if (isSilentError(error)) {
        return;
      }
      this.setData({
        errorMessage: error.message || "发送失败"
      });
    } finally {
      this.setData({
        loading: false
      });
    }
  }
});
