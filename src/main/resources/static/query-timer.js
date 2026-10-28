/* Query timer: a live running timer plus timestamps for every question sent to /ask.
   Self-contained: it adds its own styles and a small card in the bottom-right corner. */
(function () {
  if (window.__queryTimerInstalled) return;
  window.__queryTimerInstalled = true;

  function init() {
    var css = [
      '#qTimer{position:fixed;right:18px;bottom:18px;z-index:9999;min-width:230px;padding:12px 14px;border-radius:14px;',
      'background:rgba(5,20,35,.9);border:1px solid rgba(85,229,255,.28);backdrop-filter:blur(10px);color:#dffaff;',
      "font:12px/1.5 'Segoe UI',Arial,sans-serif;box-shadow:0 12px 40px rgba(0,0,0,.35)}",
      '#qTimer .qt-head{display:flex;align-items:center;gap:8px;font-weight:700;letter-spacing:1.2px;font-size:10px;color:#55e5ff}',
      '#qTimer .qt-clock{margin-left:auto;color:#9ab2c7;font-weight:600;letter-spacing:.5px}',
      '#qTimer .qt-dot{width:7px;height:7px;border-radius:50%;background:#72f2bd;box-shadow:0 0 10px #72f2bd}',
      '#qTimer.running .qt-dot{background:#55e5ff;box-shadow:0 0 12px #55e5ff;animation:qtBlink 1s infinite}',
      '#qTimer.failed .qt-dot{background:#ff8a8a;box-shadow:0 0 10px #ff8a8a}',
      '#qTimer .qt-main{margin-top:8px;font-size:18px;font-weight:800;font-variant-numeric:tabular-nums}',
      '#qTimer.running .qt-main{color:#55e5ff}#qTimer.done .qt-main{color:#72f2bd}#qTimer.failed .qt-main{color:#ff8a8a}',
      '#qTimer .qt-sub{color:#9ab2c7;font-size:11px;min-height:16px}',
      '#qTimer .qt-log{margin-top:8px;border-top:1px solid rgba(150,220,255,.14);padding-top:6px}',
      '#qTimer .qt-row{font-size:11px;color:#9ab2c7;font-variant-numeric:tabular-nums}',
      '#qTimer .qt-row.bad{color:#ff8a8a}',
      '@keyframes qtBlink{50%{opacity:.25}}',
      '@media(max-width:600px){#qTimer{left:12px;right:12px;bottom:12px}}'
    ].join('');

    var style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);

    var box = document.createElement('div');
    box.id = 'qTimer';
    box.innerHTML =
      '<div class="qt-head"><span class="qt-dot"></span><span>QUERY TIMER</span><span class="qt-clock" id="qtClock"></span></div>' +
      '<div class="qt-main" id="qtMain">Idle</div>' +
      '<div class="qt-sub" id="qtSub">Ask a question to start the timer</div>' +
      '<div class="qt-log" id="qtLog"></div>';

    document.body.appendChild(box);

    var main = document.getElementById('qtMain');
    var sub = document.getElementById('qtSub');
    var logEl = document.getElementById('qtLog');
    var clock = document.getElementById('qtClock');

    function pad(n) {
      return String(n).padStart(2, '0');
    }

    function hms(d) {
      return pad(d.getHours()) + ':' +
        pad(d.getMinutes()) + ':' +
        pad(d.getSeconds());
    }

    function fmt(ms) {
      var s = ms / 1000;

      if (s < 60) {
        return s.toFixed(1) + 's';
      }

      return Math.floor(s / 60) + 'm ' +
        pad(Math.floor(s % 60)) + 's';
    }

    function tickClock() {
      clock.textContent = hms(new Date());
    }

    tickClock();
    setInterval(tickClock, 1000);

    var ticker = null;
    var current = null;
    var entries = [];

    function renderLog() {
      logEl.textContent = '';

      entries.slice(-4).reverse().forEach(function (e) {
        var row = document.createElement('div');

        row.className = 'qt-row' + (e.ok ? '' : ' bad');
        row.textContent = e.asked + '  ' +
          (e.ok ? '\u2713' : '\u2717') + '  ' + e.took;

        logEl.appendChild(row);
      });
    }

    function start() {
      var run = {
        askedAt: new Date(),
        t0: performance.now()
      };

      current = run;

      if (ticker) {
        clearInterval(ticker);
      }

      box.className = 'running';
      sub.textContent = 'Asked at ' + hms(run.askedAt);
      main.textContent = 'Processing... 0.0s';

      ticker = setInterval(function () {
        main.textContent =
          'Processing... ' + fmt(performance.now() - run.t0);
      }, 100);

      return run;
    }

    function finish(run, ok) {
      var took = fmt(performance.now() - run.t0);

      entries.push({
        asked: hms(run.askedAt),
        took: took,
        ok: ok
      });

      if (run === current) {
        clearInterval(ticker);
        ticker = null;

        box.className = ok ? 'done' : 'failed';

        main.textContent =
          (ok ? 'Answered in ' : 'Failed after ') + took;

        sub.textContent = 'Asked ' + hms(run.askedAt) +
          ' \u00b7 finished ' + hms(new Date());
      }

      renderLog();
    }

    var origFetch = window.fetch.bind(window);

    window.fetch = function (input, init) {
      var isAsk = false;

      try {
        var url = typeof input === 'string'
          ? input
          : ((input && input.url) || String(input));

        var method = (
          (init && init.method) ||
          (input && input.method) ||
          'GET'
        ).toUpperCase();

        isAsk = method === 'POST' &&
          new URL(url, location.href).pathname === '/ask';
      } catch (e) {
        isAsk = false;
      }

      if (!isAsk) {
        return origFetch(input, init);
      }

      var run = start();

      return origFetch(input, init).then(
        function (res) {
          finish(run, res.ok);
          return res;
        },
        function (err) {
          finish(run, false);
          throw err;
        }
      );
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();


/* AI API */

async function postAsk(question, extra = {}) {
  const response = await fetch("/ask", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      question,
      ...extra
    })
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "");

    throw new Error(
      "HTTP " + response.status +
      (text ? ": " + text : "")
    );
  }

  return response.json();
}


/* FORMAT AI RESPONSE */

let pendingConfirmation = null;
const pendingMultipleConfirmations = Object.create(null);

function escapeAIText(value) {
  return String(value).replace(/[&<>"']/g, character => {
    if (character === "&") return "&amp;";
    if (character === "<") return "&lt;";
    if (character === ">") return "&gt;";
    if (character === String.fromCharCode(34)) return "&quot;";
    return "&#39;";
  });
}

function formatAIResponse(data) {
  if (!data) {
    return "No answer returned.";
  }

  // Handle multiple questions returned by the backend.
  if (
    data.status === "multiple_answers" &&
    Array.isArray(data.results)
  ) {
    Object.keys(pendingMultipleConfirmations).forEach(key => {
      delete pendingMultipleConfirmations[key];
    });

    return data.results.map((result, index) => {
      const question = result.question || `Question ${index + 1}`;
      const needsConfirmation =
        result.status === "confirmation_required";

      if (needsConfirmation) {
        pendingMultipleConfirmations[index] = {
          question: result.question || question,
          metric: result.metric || result.suggested_metric
        };
      }

      return `
        <div
          id="multi-answer-${index}"
          style="margin-top:12px;padding:12px;border-bottom:1px solid rgba(255,255,255,.1);"
        >
          <strong>Question ${index + 1}:</strong>
          ${escapeAIText(question)}

          <div class="multi-answer-content" style="margin-top:8px;">
            ${
              needsConfirmation
                ? formatConfirmationPrompt(result, index)
                : formatSingleAIResponse(result)
            }
          </div>
        </div>
      `;
    }).join("");
  }

  if (data.status === "confirmation_required") {
    const questionInput = document.getElementById("chatQuestion");

    pendingConfirmation = {
      question:
        data.question ||
        (questionInput ? questionInput.value.trim() : ""),
      metric: data.metric || data.suggested_metric
    };

    return formatConfirmationPrompt(data, null);
  }

  pendingConfirmation = null;

  return formatSingleAIResponse(data);
}


/* DISPLAY YES/NO CONFIRMATION BUTTONS */

function formatConfirmationPrompt(data, multipleIndex) {
  const metricLabel = String(
    data.suggested_metric || data.metric || ""
  ).replace(/_/g, " ");

  const confidenceText =
    typeof data.confidence === "number"
      ? `
        <div style="margin-top:6px;color:var(--muted);font-size:12px">
          Confidence: ${(data.confidence * 100).toFixed(2)}%
        </div>
      `
      : "";

  const yesHandler = multipleIndex === null
    ? "confirmSuggestedMetric()"
    : `confirmMultipleMetric(${multipleIndex})`;

  const noHandler = multipleIndex === null
    ? "rejectSuggestedMetric()"
    : `rejectMultipleMetric(${multipleIndex})`;

  return `
    <div>
      ${escapeAIText(
        data.answer || `Did you mean: ${metricLabel}?`
      )}
    </div>

    ${confidenceText}

    <div class="confirmation-actions" style="margin-top:8px;">
      <button
        type="button"
        class="confirm-yes"
        onclick="${yesHandler}">
        Yes
      </button>

      <button
        type="button"
        class="confirm-no"
        onclick="${noHandler}">
        No
      </button>
    </div>
  `;
}


/* FORMAT A SINGLE ANSWER */

function formatSingleAIResponse(data) {
  if (!data) {
    return "No answer returned.";
  }

  if (data.status === "confirmation_required") {
    return escapeAIText(
      data.answer || "Please confirm the suggested metric."
    );
  }

  if (
    data.status === "unsupported" ||
    data.status === "NOT_SUPPORTED"
  ) {
    return escapeAIText(
      data.answer || "This question is not supported yet."
    );
  }

  if (data.answer) {
    return escapeAIText(data.answer);
  }

  if (
    data.metric === "clinic_count" &&
    data.value !== undefined
  ) {
    return `
      <strong>${escapeAIText(String(data.value))}</strong>
      integrated clinics.
    `;
  }

  // This function must already exist elsewhere in your frontend.
  const entries = cleanEntries(data);

  if (entries.length) {
    return entries.map(([key, value]) => {
      const formatted = typeof value === "number"
        ? value.toLocaleString("en-IN")
        : String(value);

      return `
        <div style="
          display:flex;
          justify-content:space-between;
          gap:20px;
          padding:8px 0;
          border-bottom:1px solid rgba(255,255,255,.05);
        ">
          <span>${escapeAIText(String(key))}</span>
          <strong>${escapeAIText(formatted)}</strong>
        </div>
      `;
    }).join("");
  }

  return escapeAIText(JSON.stringify(data));
}


/* CONFIRM SUGGESTED METRIC — SINGLE QUESTION */

async function confirmSuggestedMetric() {
  if (
    !pendingConfirmation ||
    !pendingConfirmation.metric
  ) {
    return;
  }

  const answerBox = document.getElementById("aiAnswer");
  const pending = { ...pendingConfirmation };

  pendingConfirmation = null;

  answerBox.innerHTML = `
    <div class="label">AI RESPONSE</div>
    <span class="loading">Getting the confirmed result...</span>
  `;

  try {
    const data = await postAsk(pending.question, {
      confirmed: "true",
      metric: pending.metric
    });

    answerBox.innerHTML = `
      <div class="label">AI RESPONSE</div>
      ${formatAIResponse(data)}
    `;
  } catch (error) {
    console.error(error);

    answerBox.innerHTML = `
      <div class="label">AI RESPONSE</div>
      <span class="error">
        Unable to get the confirmed result.
        Please check that Spring Boot and the Python classifier are running.
      </span>
    `;
  }
}


/* REJECT SUGGESTED METRIC — SINGLE QUESTION */

function rejectSuggestedMetric() {
  pendingConfirmation = null;

  const answerBox = document.getElementById("aiAnswer");

  answerBox.innerHTML = `
    <div class="label">AI RESPONSE</div>
    Please rephrase your question or add more details,
    then click Ask AI again.
  `;

  document.getElementById("chatQuestion").focus();
}


/* CONFIRM SUGGESTED METRIC — MULTIPLE QUESTIONS */

async function confirmMultipleMetric(index) {
  const pending = pendingMultipleConfirmations[index];

  const card = document.getElementById(`multi-answer-${index}`);

  const content = card &&
    card.querySelector(".multi-answer-content");

  if (!pending || !pending.metric || !content) {
    return;
  }

  delete pendingMultipleConfirmations[index];

  content.innerHTML = `
    <span class="loading">Getting the confirmed result...</span>
  `;

  try {
    const data = await postAsk(pending.question, {
      confirmed: "true",
      metric: pending.metric
    });

    // Update only this question's card.
    if (data.status === "confirmation_required") {
      pendingMultipleConfirmations[index] = {
        question: data.question || pending.question,
        metric: data.metric || data.suggested_metric
      };

      content.innerHTML = formatConfirmationPrompt(data, index);
    } else {
      content.innerHTML = formatSingleAIResponse(data);
    }
  } catch (error) {
    console.error(error);

    content.innerHTML = `
      <span class="error">
        Unable to get the confirmed result. Please try again.
      </span>
    `;
  }
}


/* REJECT SUGGESTED METRIC — MULTIPLE QUESTIONS */

function rejectMultipleMetric(index) {
  delete pendingMultipleConfirmations[index];

  const card = document.getElementById(`multi-answer-${index}`);

  const content = card &&
    card.querySelector(".multi-answer-content");

  if (!content) {
    return;
  }

  // Reject only this question's suggestion.
  content.innerHTML = `
    Please rephrase this question or add more details, then ask again.
  `;
}