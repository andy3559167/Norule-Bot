import { createSchemaTabRenderer } from '/web/pages/tabs/schema-tab.js';

export function createNumberChainTab(deps) {
  let cancelProgressResetConfirmation = () => {};
  const text = (key, fallback) => {
    const translated = deps.i18nModule?.t(key);
    return translated && translated !== key ? translated : fallback;
  };
  return createSchemaTabRenderer({
    id: 'numberChain',
    settingsFormModule: deps.settingsFormModule,
    dirtyStateModule: deps.dirtyStateModule,
    onInit(shell) {
      const resetButton = shell.query('#resetNumberChainBtn');
      if (resetButton) {
        resetButton.onclick = () => deps.settingsFormModule.resetSection('numberChain');
      }
      const progressButton = shell.query('#resetNumberChainProgressBtn');
      if (progressButton) {
        const confirmButton = document.createElement('button');
        confirmButton.id = 'confirmResetNumberChainProgressBtn';
        confirmButton.className = 'danger';
        confirmButton.type = 'button';
        confirmButton.dataset.i18n = 'confirmResetNumberChainProgressBtn';
        confirmButton.textContent = text('confirmResetNumberChainProgressBtn', 'Confirm Reset Progress');

        const cancelButton = document.createElement('button');
        cancelButton.id = 'cancelResetNumberChainProgressBtn';
        cancelButton.className = '';
        cancelButton.type = 'button';
        cancelButton.dataset.i18n = 'cancelResetNumberChainProgressBtn';
        cancelButton.textContent = text('cancelResetNumberChainProgressBtn', 'Cancel');

        const progressControl = progressButton.closest('.field-inline');
        const confirmationActions = document.createElement('div');
        confirmationActions.className = 'number-chain-reset-confirmation hidden';
        confirmationActions.append(confirmButton, cancelButton);
        progressControl?.classList.add('number-chain-progress-control');
        progressButton.insertAdjacentElement('afterend', confirmationActions);

        let confirmationTimer = null;
        const showConfirmation = (visible) => {
          if (confirmationTimer) {
            clearTimeout(confirmationTimer);
            confirmationTimer = null;
          }
          progressControl?.classList.toggle('is-confirming', visible);
          progressButton.classList.toggle('hidden', visible);
          confirmationActions.classList.toggle('hidden', !visible);
          if (visible) {
            confirmationTimer = setTimeout(() => showConfirmation(false), 10_000);
          }
        };
        cancelProgressResetConfirmation = () => showConfirmation(false);

        progressButton.onclick = () => showConfirmation(true);
        cancelButton.onclick = () => showConfirmation(false);
        confirmButton.onclick = async () => {
          confirmButton.disabled = true;
          cancelButton.disabled = true;
          try {
            await deps.actions.resetNumberChainProgress();
          } finally {
            confirmButton.disabled = false;
            cancelButton.disabled = false;
            showConfirmation(false);
          }
        };
      }
    },
    onGuildChanged() {
      cancelProgressResetConfirmation();
    }
  });
}
