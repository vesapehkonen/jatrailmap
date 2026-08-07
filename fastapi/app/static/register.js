const registerForm = document.querySelector("#register-form");
if (registerForm) {
  const csrf = document.cookie.split("; ").find((part) => part.startsWith("csrf_token="));
  registerForm.querySelector('[name="csrf_token"]').value = csrf
    ? decodeURIComponent(csrf.slice("csrf_token=".length)) : "";
  registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    registerForm.classList.add("is-loading");
    try {
      const response = await fetch("/register", { method: "POST", body: new FormData(registerForm) });
      if (response.ok) {
        const result = await response.json();
        if (result.status === "pending") {
          registerForm.reset();
          registerForm.querySelector(".form-error").textContent = "Account created. An administrator must approve it before you can sign in.";
          return;
        }
        window.location.assign("/");
        return;
      }
      const result = await response.json().catch(() => ({}));
      registerForm.querySelector(".form-error").textContent = result.detail || "Account creation failed";
    } finally {
      registerForm.classList.remove("is-loading");
    }
  });
}
