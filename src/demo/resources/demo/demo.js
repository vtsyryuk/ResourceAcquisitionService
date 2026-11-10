const form = document.querySelector("#commands");
form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const status = document.querySelector("#status");
  const query = new URLSearchParams({ action: event.submitter.value,
    user: document.querySelector("#user").value, key: document.querySelector("#resource").value });
  try {
    const response = await fetch(`/api/command?${query}`, { method: "POST" });
    if (!response.ok) throw new Error(await response.text());
    const result = await response.json();
    status.textContent = result.result;
    document.querySelector("#result").textContent = JSON.stringify(result, null, 2);
  } catch (error) {
    status.textContent = `Request failed: ${error.message}`;
  }
});
