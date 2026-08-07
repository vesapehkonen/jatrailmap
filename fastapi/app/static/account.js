const accountStatus = document.querySelector("#account-status");
const csrf = () => {
  const item = document.cookie.split("; ").find((part) => part.startsWith("csrf_token="));
  return item ? decodeURIComponent(item.slice("csrf_token=".length)) : "";
};

async function accountApi(path, method, body) {
  const response = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf() },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const result = await response.json().catch(() => ({}));
    throw new Error(result.detail || "The request failed");
  }
  return response;
}

document.querySelector("#profile-form")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const values = Object.fromEntries(new FormData(form));
  values.show_name_on_public_trails = form.elements.show_name_on_public_trails.checked;
  values.show_location_on_public_trails = form.elements.show_location_on_public_trails.checked;
  form.classList.add("is-loading");
  try {
    await accountApi("/api/v1/account", "PATCH", values);
    accountStatus.textContent = "Profile saved.";
    form.elements.current_password.value = "";
  } catch (error) { accountStatus.textContent = error.message; }
  finally { form.classList.remove("is-loading"); }
});

document.querySelector("#password-form")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const values = Object.fromEntries(new FormData(form));
  form.classList.add("is-loading");
  try {
    await accountApi("/api/v1/account/password", "PUT", values);
    window.alert("Password changed. Please log in again.");
    window.location.assign("/");
  } catch (error) { accountStatus.textContent = error.message; }
  finally { form.classList.remove("is-loading"); }
});

document.querySelector("#delete-account-form")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!await window.confirmAction(
    "This permanently deletes your account, every trail you own, all trail photos, and your groups.",
    "Delete your account?",
    "Delete everything",
  )) return;
  form.classList.add("is-loading");
  const values = Object.fromEntries(new FormData(form));
  try {
    await accountApi("/api/v1/account", "DELETE", values);
    window.location.assign("/");
  } catch (error) { accountStatus.textContent = error.message; }
  finally { form.classList.remove("is-loading"); }
});
