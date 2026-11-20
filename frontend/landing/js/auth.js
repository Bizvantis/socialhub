// Step 36 - DOM manipulation, event listeners, form validation and API integration.
// One module drives login.html, register.html and forgot.html; each block runs only if its form exists.
import { postJson, ApiError } from "./api.js";
import { APP_URL } from "./config.js";
import { validateEmail, validateUsername, validatePassword, passwordStrength } from "./validation.js";

// ---- small DOM helpers ------------------------------------------------------

const $ = (selector) => document.querySelector(selector);

function setFieldError(input, message) {
  const errorEl = document.getElementById(`${input.id}-error`);
  if (errorEl) errorEl.textContent = message;
  // aria-invalid both styles the field (CSS attribute selector) and informs screen readers.
  input.setAttribute("aria-invalid", message ? "true" : "false");
  return !message;
}

function showFormError(message) {
  const el = $("#form-error");
  el.textContent = message;
  el.hidden = !message;
}

/** Disable the submit button while a request is in flight (prevents double submits). */
async function withBusyButton(form, label, task) {
  const button = form.querySelector('button[type="submit"]');
  const original = button.textContent;
  button.disabled = true;
  button.textContent = label;
  try {
    return await task();
  } finally {
    button.disabled = false;
    button.textContent = original;
  }
}

/**
 * Hand the session to the React app. Same origin in production (shared localStorage);
 * in development the app runs on another port, so the refresh token travels in the URL
 * fragment (#...), which browsers never send to any server. The app stores it and clears it.
 */
function goToApp(tokens) {
  localStorage.setItem("sh.refreshToken", tokens.refreshToken);
  const target = new URL(APP_URL, location.href);
  if (target.origin !== location.origin) target.hash = `handoff=${encodeURIComponent(tokens.refreshToken)}`;
  location.assign(target.toString());
}

// Event delegation: ONE listener on the document handles edits in every field. As soon as the
// user changes a field that showed an error, clear it (re-validation happens on blur/submit).
document.addEventListener("input", (event) => {
  const field = event.target;
  if (field instanceof HTMLInputElement && field.getAttribute("aria-invalid") === "true") {
    setFieldError(field, "");
  }
});

// "Show / Hide" password buttons: event listener on each button.
document.querySelectorAll(".toggle-password").forEach((button) => {
  button.addEventListener("click", () => {
    const input = document.getElementById(button.dataset.target);
    const showing = input.type === "text";
    input.type = showing ? "password" : "text";
    button.textContent = showing ? "Show" : "Hide";
    button.setAttribute("aria-label", showing ? "Show password" : "Hide password");
  });
});

// ---- login ------------------------------------------------------------------------

const loginForm = $("#login-form");
if (loginForm) {
  const id = $("#login-id");
  const password = $("#login-password");

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault(); // stop the browser's full-page form submission
    showFormError("");
    const ok = [
      setFieldError(id, id.value.trim() ? "" : "Enter your email or username."),
      setFieldError(password, password.value ? "" : "Enter your password."),
    ].every(Boolean);
    if (!ok) return;

    try {
      const tokens = await withBusyButton(loginForm, "Logging in...", () =>
        postJson("/api/auth/login", { emailOrUsername: id.value.trim(), password: password.value }));
      goToApp(tokens);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) showFormError("Wrong email/username or password.");
      else if (err instanceof ApiError && err.status === 429) showFormError("Too many attempts. Wait a moment and try again.");
      else showFormError(err.message);
    }
  });
}

// ---- register -----------------------------------------------------------------------

const registerForm = $("#register-form");
if (registerForm) {
  const email = $("#reg-email");
  const username = $("#reg-username");
  const password = $("#reg-password");
  const confirm = $("#reg-confirm");
  const terms = $("#reg-terms");

  // Validate on blur (when leaving a field) rather than on every keystroke: less noisy.
  email.addEventListener("blur", () => setFieldError(email, validateEmail(email.value)));
  username.addEventListener("blur", () => setFieldError(username, validateUsername(username.value)));

  // Live password strength meter: "input" fires on every change.
  password.addEventListener("input", () => {
    const score = passwordStrength(password.value);
    const bar = $("#strength-bar");
    bar.style.width = `${(score / 4) * 100}%`;
    bar.style.background = ["#dc2626", "#dc2626", "#f97316", "#eab308", "#16a34a"][score];
    $("#strength-label").textContent = ["Too short", "Weak", "Fair", "Good", "Strong"][score];
  });

  registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    showFormError("");
    const checks = [
      setFieldError(email, validateEmail(email.value)),
      setFieldError(username, validateUsername(username.value)),
      setFieldError(password, validatePassword(password.value)),
      setFieldError(confirm, confirm.value === password.value ? "" : "Passwords do not match."),
      setFieldError(terms, terms.checked ? "" : "Please accept the guidelines."),
    ];
    if (!checks.every(Boolean)) {
      registerForm.querySelector('[aria-invalid="true"]')?.focus();
      return;
    }

    try {
      await withBusyButton(registerForm, "Creating account...", async () => {
        // Two requests in sequence: create the account, then log straight in.
        await postJson("/api/auth/register", {
          email: email.value.trim(), username: username.value.trim(), password: password.value,
        });
        const tokens = await postJson("/api/auth/login", { emailOrUsername: username.value.trim(), password: password.value });
        goToApp(tokens);
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // The server says which one is taken; attach the message to the right field.
        if (/email/i.test(err.message)) setFieldError(email, err.message);
        else setFieldError(username, err.message);
      } else {
        showFormError(err.message);
      }
    }
  });
}

// ---- forgot password ------------------------------------------------------------------

const forgotForm = $("#forgot-form");
if (forgotForm) {
  const email = $("#forgot-email");
  forgotForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    showFormError("");
    if (!setFieldError(email, validateEmail(email.value))) return;
    try {
      await withBusyButton(forgotForm, "Sending...", () => postJson("/api/auth/password/forgot", { email: email.value.trim() }));
      const ok = $("#form-success");
      ok.textContent = "If an account exists for that email, a reset link is on its way.";
      ok.hidden = false;
    } catch (err) {
      showFormError(err.message);
    }
  });
}
