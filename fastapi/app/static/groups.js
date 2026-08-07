const groupStatus = document.querySelector("#group-status");
const groupCsrf = () => {
  const item = document.cookie.split("; ").find((part) => part.startsWith("csrf_token="));
  return item ? decodeURIComponent(item.slice("csrf_token=".length)) : "";
};

async function groupApi(url, method, body) {
  const response = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json", "X-CSRF-Token": groupCsrf() },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const result = await response.json().catch(() => ({}));
    throw new Error(result.detail || "The group could not be saved");
  }
}

document.querySelectorAll(".group-form").forEach((form) => {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const values = new FormData(form);
    form.classList.add("is-loading");
    try {
      await groupApi(form.dataset.url, form.dataset.method, {
        name: values.get("name"), members: values.getAll("members"),
      });
      window.location.reload();
    } catch (error) { groupStatus.textContent = error.message; }
    finally { form.classList.remove("is-loading"); }
  });
});

document.querySelectorAll(".delete-group").forEach((button) => {
  button.addEventListener("click", async () => {
    if (!await window.confirmAction(
      "Group-only content without another group will become private.",
      "Delete this group?",
      "Delete group",
    )) return;
    try {
      await groupApi(button.dataset.url, "DELETE");
      window.location.reload();
    } catch (error) { groupStatus.textContent = error.message; }
  });
});
