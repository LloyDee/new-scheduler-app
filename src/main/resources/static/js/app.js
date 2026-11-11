(() => {
function initializePage() {
document.querySelectorAll("[data-phone]").forEach((input) => {
  const formatPhone = () => {
    const caret = input.selectionStart;
    const digitsBeforeCaret = input.value.slice(0, caret).replace(/[^0-9]/g, "").length;
    const digits = input.value.replace(/[^0-9]/g, "").slice(0, 10);
    let formatted = digits ? `(${digits.slice(0, 3)}` : "";
    if (digits.length > 3) formatted += `)${digits.slice(3, 6)}`;
    if (digits.length > 6) formatted += `-${digits.slice(6)}`;
    input.value = formatted;
    if (document.activeElement === input && caret !== null) {
      let position = 0;
      let count = 0;
      while (position < formatted.length && count < digitsBeforeCaret) {
        if (/[0-9]/.test(formatted[position])) count++;
        position++;
      }
      input.setSelectionRange(position, position);
    }
    input.setCustomValidity(digits.length && digits.length !== 10 ? "Enter a 10-digit phone number." : "");
  };
  input.addEventListener("input", formatPhone);
  formatPhone();
});

document.querySelectorAll("[data-auto-submit]").forEach((select) => {
  select.addEventListener("change", () => select.form.requestSubmit());
});

document.querySelectorAll("[data-department-picker]").forEach((picker) => {
  const departmentSelect = picker.querySelector("[data-department-select]");
  if (!departmentSelect) return;
  const memberships = [...picker.querySelectorAll("[data-department-membership]")];
  const administrator = picker.closest("form").querySelector("[name=eventAdministrator]");
  const updateOptions = () => {
    for (const option of departmentSelect.options) {
      if (!option.value || option.value === "none") continue;
      option.disabled = memberships.some((row) =>
        row.dataset.departmentId === option.value && !row.hidden);
    }
    departmentSelect.required = administrator?.value !== "true" && !memberships.some((row) => !row.hidden);
  };
  administrator?.addEventListener("change", updateOptions);
  departmentSelect.addEventListener("change", () => {
    if (departmentSelect.value === "none") {
      memberships.forEach((row) => {
        const role = row.querySelector("select");
        role.value = "";
        role.required = false;
        row.hidden = true;
      });
      updateOptions();
      return;
    }
    const row = memberships.find((item) => item.dataset.departmentId === departmentSelect.value);
    if (!row) return;
    row.hidden = false;
    const role = row.querySelector("select");
    role.required = true;
    departmentSelect.value = "";
    updateOptions();
    role.focus();
  });
  memberships.forEach((row) => {
    row.querySelector("[data-remove-department]").addEventListener("click", () => {
      const role = row.querySelector("select");
      role.value = "";
      role.required = false;
      row.hidden = true;
      updateOptions();
      departmentSelect.focus();
    });
  });
  updateOptions();
});

document.querySelectorAll("[data-notification]").forEach((notification) => {
  if (notification.dataset.notification !== "success") return;
  window.setTimeout(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      notification.remove();
      return;
    }
    const fade = notification.animate([{ opacity: 1 }, { opacity: 0 }], {
      duration: 400,
      easing: "ease-out",
      fill: "forwards",
    });
    fade.finished.then(() => notification.remove());
  }, 2500);
});

const eventForm = document.querySelector("[data-event-form]");
if (eventForm) {
  const type = eventForm.elements.type;
  const startDate = eventForm.elements.startDate;
  const assembly = eventForm.elements.assembly;
  const preview = eventForm.querySelector("[data-event-preview]");
  const updateEvent = () => {
    const isAssembly = type.value === "CIRCUIT_ASSEMBLY";
    eventForm.querySelector("[data-assembly-field]").hidden = !isAssembly;
    assembly.required = isAssembly;
    assembly.disabled = !isAssembly;
    eventForm.querySelector("[data-circuit-field]").hidden = !isAssembly;
    eventForm.elements.circuit.disabled = !isAssembly;
    preview.textContent = "";
    if (!type.value || !startDate.value) return;
    const start = new Date(`${startDate.value}T00:00:00Z`);
    if (Number.isNaN(start.getTime())) return;
    const end = new Date(start);
    end.setUTCDate(end.getUTCDate() + (isAssembly ? 0 : 2));
    const year = start.getUTCFullYear() + (start.getUTCMonth() >= 8 ? 1 : 0);
    const name = isAssembly ? `Circuit Assembly ${assembly.value}` : `${year} Regional Convention`;
    preview.textContent = `${name.trim()} · End date: ${end.toISOString().slice(0, 10)}`;
  };
  eventForm.addEventListener("change", updateEvent);
  updateEvent();
}

}
Scheduler.page(initializePage);

// Capture invalid events because native constraint validation does not bubble.
let fieldErrorId = 0;
const fieldErrors = new WeakMap();
function updateFieldError(input) {
  let error = fieldErrors.get(input);
  if (input.validity.valid || !input.willValidate) {
    if (!error) return;
    error.remove();
    const descriptions = (input.getAttribute('aria-describedby') || '').split(/\s+/).filter(id => id && id !== error.id);
    if (descriptions.length) input.setAttribute('aria-describedby', descriptions.join(' '));
    else input.removeAttribute('aria-describedby');
    input.removeAttribute('aria-invalid');
    fieldErrors.delete(input);
    return;
  }
  if (!error) {
    error = document.createElement('span');
    error.id = `validation-error-${++fieldErrorId}`;
    error.className = 'field-error';
    error.setAttribute('aria-live', 'polite');
    input.insertAdjacentElement('afterend', error);
    input.setAttribute('aria-describedby', [input.getAttribute('aria-describedby'), error.id].filter(Boolean).join(' '));
    fieldErrors.set(input, error);
  }
  input.setAttribute('aria-invalid', 'true');
  error.textContent = input.validationMessage;
}
document.addEventListener('invalid', event => updateFieldError(event.target), true);
for (const type of ['input', 'change']) {
  document.addEventListener(type, event => {
    if (fieldErrors.has(event.target)) updateFieldError(event.target);
  });
}
document.addEventListener('click', event => {
  event.target.closest('[data-dismiss-notification]')?.closest('[data-notification]')?.remove();
});

document.addEventListener("submit", (event) => {
  const message = event.target.dataset.confirm;
  if (message && !window.confirm(message)) event.preventDefault();
});
document.addEventListener("click", (event) => {
  if (event.target.closest("[data-print]")) window.print();
});

Scheduler.menus('more-navigation', { toggle: '.more-toggle', items: '.more-menu', blur: true });
})();
