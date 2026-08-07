function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function formatInline(text) {
  const tokens = [];
  const tokenized = String(text || "").replace(/`([^`]+)`/g, (_, code) => {
    const token = `__CODE_${tokens.length}__`;
    tokens.push(`<code style="padding: 0 8rpx; border-radius: 8rpx; background: rgba(31, 122, 74, 0.08); color: #1d7d4d; font-family: monospace; font-size: 0.92em;">${escapeHtml(code)}</code>`);
    return token;
  });

  let html = escapeHtml(tokenized);
  html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" style="color:#1d7d4d;text-decoration:underline;">$1</a>');
  html = html.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  html = html.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, "$1<em>$2</em>");

  tokens.forEach((value, index) => {
    html = html.replace(new RegExp(`__CODE_${index}__`, "g"), value);
  });

  return html.replace(/\n/g, "<br />");
}

function markdownToHtml(markdown) {
  const lines = String(markdown || "").replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let paragraph = [];
  let listType = "";
  let listItems = [];
  let quoteLines = [];
  let codeFence = "";
  let codeLines = [];

  const flushParagraph = () => {
    if (!paragraph.length) {
      return;
    }
    blocks.push(`<p style="margin: 0 0 12rpx; line-height: 1.7;">${formatInline(paragraph.join(" "))}</p>`);
    paragraph = [];
  };

  const flushList = () => {
    if (!listItems.length) {
      return;
    }
    const tag = listType === "ol" ? "ol" : "ul";
    const listStyle = listType === "ol" ? "padding-left: 34rpx; margin: 0 0 12rpx;" : "padding-left: 32rpx; margin: 0 0 12rpx;";
    const items = listItems
      .map((item) => `<li style="margin: 0 0 6rpx; line-height: 1.7;">${formatInline(item)}</li>`)
      .join("");
    blocks.push(`<${tag} style="${listStyle}">${items}</${tag}>`);
    listItems = [];
    listType = "";
  };

  const flushQuote = () => {
    if (!quoteLines.length) {
      return;
    }
    blocks.push(
      `<blockquote style="margin: 0 0 12rpx; padding: 10rpx 14rpx; border-left: 6rpx solid rgba(29, 125, 77, 0.35); background: rgba(31, 122, 74, 0.06); color: #355542;">${formatInline(quoteLines.join("\n"))}</blockquote>`,
    );
    quoteLines = [];
  };

  const flushCode = () => {
    if (!codeFence) {
      return;
    }
    blocks.push(
      `<pre style="margin: 0 0 12rpx; padding: 14rpx 16rpx; border-radius: 18rpx; overflow: auto; background: #183526; color: #f3fff6; white-space: pre-wrap; word-break: break-word;"><code style="font-family: monospace; font-size: 0.92em;">${escapeHtml(codeLines.join("\n"))}</code></pre>`,
    );
    codeFence = "";
    codeLines = [];
  };

  const flushAll = () => {
    flushParagraph();
    flushList();
    flushQuote();
    flushCode();
  };

  lines.forEach((line) => {
    const trimmed = line.trim();

    if (codeFence) {
      if (trimmed.startsWith(codeFence)) {
        flushCode();
      } else {
        codeLines.push(line);
      }
      return;
    }

    if (!trimmed) {
      flushAll();
      return;
    }

    const fenceMatch = line.match(/^(```+|~~~+)\s*$/);
    if (fenceMatch) {
      flushAll();
      codeFence = fenceMatch[1].slice(0, 3);
      return;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      flushAll();
      const level = headingMatch[1].length;
      const size = [0, 42, 36, 32, 28, 24, 22][level];
      blocks.push(
        `<h${level} style="margin: 0 0 12rpx; font-size: ${size}rpx; line-height: 1.35; color: #183526;">${formatInline(headingMatch[2])}</h${level}>`,
      );
      return;
    }

    const quoteMatch = line.match(/^>\s?(.*)$/);
    if (quoteMatch) {
      flushParagraph();
      flushList();
      quoteLines.push(quoteMatch[1]);
      return;
    }

    const unorderedMatch = line.match(/^[-*+]\s+(.+)$/);
    if (unorderedMatch) {
      flushParagraph();
      flushQuote();
      if (listType && listType !== "ul") {
        flushList();
      }
      listType = "ul";
      listItems.push(unorderedMatch[1]);
      return;
    }

    const orderedMatch = line.match(/^\d+\.\s+(.+)$/);
    if (orderedMatch) {
      flushParagraph();
      flushQuote();
      if (listType && listType !== "ol") {
        flushList();
      }
      listType = "ol";
      listItems.push(orderedMatch[1]);
      return;
    }

    flushQuote();
    flushList();
    paragraph.push(line);
  });

  flushAll();

  return `<div style="font-size: 28rpx; line-height: 1.7; color: #1f3527;">${blocks.join("")}</div>`;
}

module.exports = {
  markdownToHtml
};
