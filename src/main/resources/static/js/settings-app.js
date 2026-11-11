(() => {
function initializeSettings() {
document.querySelectorAll('[data-app-dialog]').forEach((button) => {
    button.addEventListener('click', () => {
        const dialog = document.getElementById(button.dataset.appDialog);
        if (dialog && !dialog.open) {
            SchedulerDialogs.open(dialog, button);
        }
    });
});
}
document.addEventListener('scheduler:page', initializeSettings);
initializeSettings();
})();
