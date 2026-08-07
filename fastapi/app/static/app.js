function cookie(name) {
  const prefix = `${name}=`;
  const item = document.cookie.split("; ").find((part) => part.startsWith(prefix));
  return item ? decodeURIComponent(item.slice(prefix.length)) : "";
}

document.querySelectorAll(".csrf-form input[name=csrf_token]").forEach((input) => {
  input.value = cookie("csrf_token");
});

const loginForm = document.querySelector("#login-form");
if (loginForm) {
  loginForm.querySelector("input[name=csrf_token]").value = cookie("csrf_token");
  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    loginForm.classList.add("is-loading");
    try {
      const response = await fetch("/login", { method: "POST", body: new FormData(loginForm) });
      if (response.ok) {
        window.location.reload();
        return;
      }
      const result = await response.json();
      loginForm.querySelector(".form-error").textContent = result.detail || "Login failed";
    } finally {
      loginForm.classList.remove("is-loading");
    }
  });
}

const signInDialog = document.querySelector("#sign-in");
const openSignIn = document.querySelector("#open-sign-in");
const closeSignIn = document.querySelector("#close-sign-in");
if (signInDialog && openSignIn) {
  const showSignIn = () => {
    if (signInDialog.showModal) signInDialog.showModal();
    else signInDialog.setAttribute("open", "");
    signInDialog.querySelector('[name="username"]')?.focus();
  };
  openSignIn.addEventListener("click", showSignIn);
  closeSignIn?.addEventListener("click", () => signInDialog.close());
  signInDialog.addEventListener("click", (event) => {
    if (event.target === signInDialog) signInDialog.close();
  });
  if (window.location.hash === "#sign-in") showSignIn();
}

document.querySelectorAll('input[type="password"]').forEach((input) => {
  const wrapper = document.createElement("span");
  wrapper.className = "password-input";
  input.parentNode.insertBefore(wrapper, input);
  wrapper.appendChild(input);
  const toggle = document.createElement("button");
  toggle.type = "button";
  toggle.className = "password-toggle";
  toggle.textContent = "Show";
  toggle.setAttribute("aria-label", "Show password");
  toggle.addEventListener("click", () => {
    const show = input.type === "password";
    input.type = show ? "text" : "password";
    toggle.textContent = show ? "Hide" : "Show";
    toggle.setAttribute("aria-label", show ? "Hide password" : "Show password");
  });
  wrapper.appendChild(toggle);
});

const navigationToggle = document.querySelector(".nav-toggle");
const navigation = document.querySelector(".site-navigation");
if (navigationToggle && navigation) {
  navigationToggle.addEventListener("click", () => {
    const open = navigationToggle.getAttribute("aria-expanded") !== "true";
    navigationToggle.setAttribute("aria-expanded", String(open));
    navigationToggle.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
    navigation.classList.toggle("is-open", open);
  });
  navigation.querySelectorAll("a").forEach((link) => link.addEventListener("click", () => {
    navigationToggle.setAttribute("aria-expanded", "false");
    navigation.classList.remove("is-open");
  }));
}

const confirmationDialog = document.querySelector("#confirm-dialog");
window.confirmAction = (message, title = "Confirm action", confirmLabel = "Confirm") => {
  if (!confirmationDialog?.showModal) return Promise.resolve(window.confirm(message));
  confirmationDialog.querySelector("#confirm-title").textContent = title;
  confirmationDialog.querySelector("#confirm-message").textContent = message;
  confirmationDialog.querySelector('button[value="confirm"]').textContent = confirmLabel;
  confirmationDialog.showModal();
  return new Promise((resolve) => {
    confirmationDialog.addEventListener("close", () => {
      resolve(confirmationDialog.returnValue === "confirm");
    }, { once: true });
  });
};
