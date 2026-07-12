const form = document.getElementById('shortUrlForm');
const urlInput = document.getElementById('targetUrl');
const codeInput = document.getElementById('customCode');
const submitButton = document.getElementById('createShortUrlBtn');
const resultCard = document.getElementById('resultCard');
const resultUrl = document.getElementById('resultUrl');
const resultTarget = document.getElementById('resultTarget');
const errorText = document.getElementById('errorText');
const copyButton = document.getElementById('copyShortUrlBtn');
const uiLangSelect = document.getElementById('uiLangSelect');

const imageShareForm = document.getElementById('imageShareForm');
const imageFile = document.getElementById('imageFile');
const imageFileName = document.getElementById('imageFileName');
const imageRetentionPreset = document.getElementById('imageRetentionPreset');
const imageCustomExpirationField = document.getElementById('imageCustomExpirationField');
const imageCustomExpiration = document.getElementById('imageCustomExpiration');
const imagePasswordProtected = document.getElementById('imagePasswordProtected');
const imagePasswordField = document.getElementById('imagePasswordField');
const imagePassword = document.getElementById('imagePassword');
const imagePasswordVisibility = document.getElementById('imagePasswordVisibility');
const imagePasswordVisibilityText = document.getElementById('imagePasswordVisibilityText');
const createImageShareButton = document.getElementById('createImageShareBtn');
const imageErrorText = document.getElementById('imageErrorText');
const imageResultCard = document.getElementById('imageResultCard');
const imageResultUrl = document.getElementById('imageResultUrl');
const imageExpiresAt = document.getElementById('imageExpiresAt');
const copyImageShareButton = document.getElementById('copyImageShareBtn');

const textElements = {
  uiLangLabel: document.getElementById('uiLangLabel'),
  shortUrlTitle: document.getElementById('shortUrlTitle'),
  shortUrlSubtitle: document.getElementById('shortUrlSubtitle'),
  shortLinkEyebrow: document.getElementById('shortLinkEyebrow'),
  shortLinkTitle: document.getElementById('shortLinkTitle'),
  targetUrlLabel: document.getElementById('targetUrlLabel'),
  customCodeLabel: document.getElementById('customCodeLabel'),
  resultTitle: document.getElementById('resultTitle'),
  resultTargetLabel: document.getElementById('resultTargetLabel'),
  imageShareTitle: document.getElementById('imageShareTitle'),
  imageShareSubtitle: document.getElementById('imageShareSubtitle'),
  imageFileLabel: document.getElementById('imageFileLabel'),
  imageLimitHint: document.getElementById('imageLimitHint'),
  imageRetentionLabel: document.getElementById('imageRetentionLabel'),
  imageRetentionHint: document.getElementById('imageRetentionHint'),
  imageCustomExpirationLabel: document.getElementById('imageCustomExpirationLabel'),
  imageCustomExpirationHint: document.getElementById('imageCustomExpirationHint'),
  imagePasswordProtectedLabel: document.getElementById('imagePasswordProtectedLabel'),
  imagePasswordLabel: document.getElementById('imagePasswordLabel'),
  imagePasswordHint: document.getElementById('imagePasswordHint'),
  imageResultTitle: document.getElementById('imageResultTitle'),
  imageExpiresLabel: document.getElementById('imageExpiresLabel')
};

if ([
  form, urlInput, codeInput, submitButton, resultCard, resultUrl, resultTarget, errorText, copyButton, uiLangSelect,
  imageShareForm, imageFile, imageFileName, imageRetentionPreset, imageCustomExpirationField, imageCustomExpiration,
  imagePasswordProtected, imagePasswordField,
  imagePassword, imagePasswordVisibility, imagePasswordVisibilityText,
  createImageShareButton, imageErrorText, imageResultCard, imageResultUrl, imageExpiresAt,
  copyImageShareButton, ...Object.values(textElements)
].some((element) => !element)) {
  throw new Error('Short URL page is missing required DOM elements.');
}

const LANG_STORAGE_KEY = 'norule.shorturl.ui.lang';
const DEFAULT_LANG = 'zh-TW';
const I18N = {
  'zh-TW': {
    pageTitle: '\u77ed\u7db2\u5740',
    uiLangLabel: '\u8a9e\u8a00',
    shortUrlTitle: '\u5206\u4eab\uff0c\u4e0d\u9700\u5197\u9577\u9023\u7d50',
    shortUrlSubtitle: '\u5efa\u7acb\u7c21\u6f54\u7684\u9023\u7d50\uff0c\u6216\u4e0a\u50b3\u5716\u7247\u5f8c\u76f4\u63a5\u5206\u4eab\u3002',
    shortLinkEyebrow: '\u9023\u7d50',
    shortLinkTitle: '\u7e2e\u77ed\u7db2\u5740',
    targetUrlLabel: '\u76ee\u6a19\u7db2\u5740',
    targetUrlPlaceholder: 'https://example.com/path',
    customCodeLabel: '\u81ea\u8a02\u4ee3\u78bc\uff08\u9078\u586b\uff09',
    customCodePlaceholder: 'my-link',
    createButton: '\u5efa\u7acb\u77ed\u7db2\u5740',
    creatingButton: '\u5efa\u7acb\u4e2d...',
    resultTitle: '\u4f60\u7684\u9023\u7d50',
    resultTargetLabel: '\u76ee\u6a19',
    copyButton: '\u8907\u88fd\u9023\u7d50',
    copiedButton: '\u5df2\u8907\u88fd',
    imageShareTitle: '\u5206\u4eab\u5716\u7247\u6216\u5f71\u7247',
    imageShareSubtitle: '\u4e0a\u50b3\u4e00\u6b21\uff0c\u7528\u4e00\u500b\u77ed\u9023\u7d50\u5206\u4eab\u5716\u7247\u6216\u5f71\u7247\u3002',
    imageFileLabel: '\u9078\u64c7\u5716\u7247\u6216\u5f71\u7247',
    imageLimitHint: '\u5716\u7247\u652f\u63f4 PNG\u3001JPEG\u3001GIF \u8207 WebP\uff08\u6700\u5927 {imageMax} MB\uff09\uff1b\u5f71\u7247\u652f\u63f4 MP4 \u8207 WebM\uff08\u6700\u5927 {videoMax} MB\u3001\u6700\u9577 {minutes} \u5206\u9418\uff09\u3002',
    imageRetentionLabel: '\u5230\u671f\u6642\u9593',
    imageRetentionHint: '\u9078\u64c7\u56fa\u5b9a\u6642\u9593\uff0c\u6216\u81ea\u8a02\u5230\u671f\u6642\u9593\uff08\u6700\u591a {days} \u5929\uff09\u3002',
    imageCustomExpirationLabel: '\u81ea\u8a02\u5230\u671f\u6642\u9593',
    imageCustomExpirationHint: '\u8acb\u9078\u64c7\u672a\u4f86\u7684\u65e5\u671f\u8207\u6642\u9593\u3002',
    retention5Minutes: '5 \u5206\u9418',
    retention10Minutes: '10 \u5206\u9418',
    retention30Minutes: '30 \u5206\u9418',
    retention1Hour: '1 \u5c0f\u6642',
    retention3Hours: '3 \u5c0f\u6642',
    retention6Hours: '6 \u5c0f\u6642',
    retention12Hours: '12 \u5c0f\u6642',
    retention24Hours: '24 \u5c0f\u6642',
    retentionCustom: '\u81ea\u8a02\u5230\u671f\u6642\u9593',
    imagePasswordProtectedLabel: '\u4f7f\u7528\u5bc6\u78bc\u4fdd\u8b77',
    imagePasswordLabel: '\u5bc6\u78bc',
    imagePasswordHint: '\u9810\u8a2d\u70ba\u4eca\u5929\u7684\u56db\u4f4d\u6578\u5b57\uff08MMDD\uff09\uff0c\u4e5f\u53ef\u4ee5\u81ea\u884c\u4fee\u6539\u3002',
    showPassword: '\u986f\u793a',
    hidePassword: '\u96b1\u85cf',
    createImageShareButton: '\u5efa\u7acb\u5a92\u9ad4\u9023\u7d50',
    creatingImageShareButton: '\u4e0a\u50b3\u4e2d...',
    imageResultTitle: '\u4f60\u7684\u5a92\u9ad4\u9023\u7d50',
    imageExpiresLabel: '\u5230\u671f\u6642\u9593',
    errorEnterUrl: '\u8acb\u8f38\u5165\u7db2\u5740\u3002',
    errorCreateFailed: '\u5efa\u7acb\u77ed\u7db2\u5740\u5931\u6557\u3002',
    errorRequestFailed: '\u8acb\u6c42\u5931\u6557\u3002',
    errorClipboardDenied: '\u526a\u8cbc\u7c3f\u6b0a\u9650\u88ab\u62d2\u7d55\u3002',
    errorInvalidUrlOrCode: '\u7db2\u5740\u6216\u81ea\u8a02\u4ee3\u78bc\u7121\u6548\u3002',
    errorMissingUrl: '\u7f3a\u5c11\u7db2\u5740\u53c3\u6578\u3002',
    errorMethodNotAllowed: '\u4e0d\u652f\u63f4\u7684\u8acb\u6c42\u65b9\u5f0f\u3002',
    errorMissingShortUrl: '\u56de\u61c9\u7f3a\u5c11\u77ed\u7db2\u5740\u6b04\u4f4d\u3002',
    errorSelectImage: '\u8acb\u9078\u64c7\u5716\u7247\u6216\u5f71\u7247\u6a94\u6848\u3002',
    errorImageTooLarge: '\u5716\u7247\u8d85\u904e\u5141\u8a31\u7684\u5927\u5c0f\u3002',
    errorUnsupportedImage: '\u50c5\u652f\u63f4 PNG\u3001JPEG\u3001GIF \u8207 WebP \u5716\u7247\u3002',
    errorUnsupportedMedia: '\u50c5\u652f\u63f4 PNG\u3001JPEG\u3001GIF\u3001WebP\u3001MP4 \u8207 WebM \u6a94\u6848\u3002',
    errorMediaTooLarge: '\u4e0a\u50b3\u6a94\u6848\u8d85\u904e\u5141\u8a31\u7684\u5927\u5c0f\u3002',
    errorVideoTooLarge: '\u5f71\u7247\u8d85\u904e\u5141\u8a31\u7684\u5927\u5c0f\u3002',
    errorVideoTooLong: '\u5f71\u7247\u9577\u5ea6\u4e0d\u53ef\u8d85\u904e {minutes} \u5206\u9418\u3002',
    errorVideoMetadata: '\u7121\u6cd5\u8b80\u53d6\u5f71\u7247\u9577\u5ea6\uff0c\u8acb\u78ba\u8a8d\u6a94\u6848\u5b8c\u6574\u3002',
    errorRetentionTooLong: '\u4fdd\u5b58\u6642\u9593\u8d85\u904e\u5141\u8a31\u7bc4\u570d\u3002',
    errorInvalidCustomExpiration: '\u8acb\u9078\u64c7\u672a\u4f86 {days} \u5929\u5167\u7684\u5230\u671f\u6642\u9593\u3002',
    errorInvalidImagePassword: '\u5bc6\u78bc\u9700\u70ba 4\uff5e128 \u500b\u5b57\u5143\u3002',
    errorImageSharingDisabled: '\u5a92\u9ad4\u5206\u4eab\u529f\u80fd\u672a\u958b\u555f\u3002',
    errorImageUploadFailed: '\u5a92\u9ad4\u4e0a\u50b3\u5931\u6557\u3002',
    errorMediaStorageFailed: '\u5a92\u9ad4\u6a94\u6848\u5132\u5b58\u5931\u6557\uff0c\u8acb\u78ba\u8a8d\u4f3a\u670d\u5668\u7684\u5132\u5b58\u8def\u5f91\u8207\u5beb\u5165\u6b0a\u9650\u3002',
    errorMediaPersistenceFailed: '\u5a92\u9ad4\u5206\u4eab\u7d00\u9304\u5132\u5b58\u5931\u6557\uff0c\u8acb\u78ba\u8a8d\u8cc7\u6599\u5eab\u9023\u7dda\u8207\u6b0a\u9650\u3002',
    errorMediaGatewayFailed: '\u4e0a\u50b3\u8acb\u6c42\u88ab\u4e0a\u6e38\u7db2\u95dc\u62d2\u7d55\uff08HTTP {status}\uff09\uff0c\u8acb\u6aa2\u67e5\u7db2\u95dc\u4e0a\u50b3\u5927\u5c0f\u9650\u5236\u3002'
  },
  en: {
    pageTitle: 'Short URL',
    uiLangLabel: 'Language',
    shortUrlTitle: 'Share without the long link.',
    shortUrlSubtitle: 'Create a compact link or upload an image to share in seconds.',
    shortLinkEyebrow: 'LINK',
    shortLinkTitle: 'Shorten a link',
    targetUrlLabel: 'Destination URL',
    targetUrlPlaceholder: 'https://example.com/path',
    customCodeLabel: 'Custom code (optional)',
    customCodePlaceholder: 'my-link',
    createButton: 'Create short URL',
    creatingButton: 'Creating...',
    resultTitle: 'Your link',
    resultTargetLabel: 'Destination',
    copyButton: 'Copy link',
    copiedButton: 'Copied',
    imageShareTitle: 'Share an image or video',
    imageShareSubtitle: 'Upload an image or video once and share it with a clean short link.',
    imageFileLabel: 'Choose an image or video',
    imageLimitHint: 'PNG, JPEG, GIF, and WebP up to {imageMax} MB; MP4 and WebM up to {videoMax} MB and {minutes} minutes.',
    imageRetentionLabel: 'Expiration',
    imageRetentionHint: 'Choose a preset or a custom expiration (up to {days} days).',
    imageCustomExpirationLabel: 'Custom expiration',
    imageCustomExpirationHint: 'Choose a future date and time.',
    retention5Minutes: '5 minutes',
    retention10Minutes: '10 minutes',
    retention30Minutes: '30 minutes',
    retention1Hour: '1 hour',
    retention3Hours: '3 hours',
    retention6Hours: '6 hours',
    retention12Hours: '12 hours',
    retention24Hours: '24 hours',
    retentionCustom: 'Custom expiration',
    imagePasswordProtectedLabel: 'Protect with password',
    imagePasswordLabel: 'Password',
    imagePasswordHint: 'Defaults to today\'s four digits (MMDD); you can change it.',
    showPassword: 'Show',
    hidePassword: 'Hide',
    createImageShareButton: 'Create media link',
    creatingImageShareButton: 'Uploading...',
    imageResultTitle: 'Your media link',
    imageExpiresLabel: 'Expires',
    errorEnterUrl: 'Please enter a URL.',
    errorCreateFailed: 'Failed to create short URL.',
    errorRequestFailed: 'Request failed.',
    errorClipboardDenied: 'Clipboard permission denied.',
    errorInvalidUrlOrCode: 'Invalid URL or custom code.',
    errorMissingUrl: 'Missing URL parameter.',
    errorMethodNotAllowed: 'Method not allowed.',
    errorMissingShortUrl: 'Response missing short URL field.',
    errorSelectImage: 'Select an image or video file.',
    errorImageTooLarge: 'The image exceeds the allowed size.',
    errorUnsupportedImage: 'Only PNG, JPEG, GIF, and WebP images are supported.',
    errorUnsupportedMedia: 'Only PNG, JPEG, GIF, WebP, MP4, and WebM files are supported.',
    errorMediaTooLarge: 'The uploaded file exceeds the allowed size.',
    errorVideoTooLarge: 'The video exceeds the allowed size.',
    errorVideoTooLong: 'The video must not exceed {minutes} minutes.',
    errorVideoMetadata: 'The video duration could not be read. Check that the file is valid.',
    errorRetentionTooLong: 'Retention is outside the allowed range.',
    errorInvalidCustomExpiration: 'Choose an expiration within the next {days} days.',
    errorInvalidImagePassword: 'Password must contain 4 to 128 characters.',
    errorImageSharingDisabled: 'Media sharing is disabled.',
    errorImageUploadFailed: 'Media upload failed.',
    errorMediaStorageFailed: 'The media file could not be stored. Check the server storage path and write permission.',
    errorMediaPersistenceFailed: 'The media share record could not be saved. Check the database connection and permission.',
    errorMediaGatewayFailed: 'The upload was rejected by the upstream gateway (HTTP {status}). Check its request size limit.'
  }
};

let currentLang = DEFAULT_LANG;
let imageShareConfig = {
  enabled: true,
  defaultRetentionHours: 1,
  maxRetentionDays: 365,
  maxFileSizeBytes: 20 * 1024 * 1024,
  maxFileSizeMb: 20,
  maxVideoFileSizeBytes: 100 * 1024 * 1024,
  maxVideoFileSizeMb: 100,
  maxVideoDurationSeconds: 5 * 60
};

const t = (key) => I18N[currentLang]?.[key] ?? I18N[DEFAULT_LANG][key] ?? key;
const format = (text, values) => Object.entries(values).reduce(
  (result, [key, value]) => result.replaceAll(`{${key}}`, String(value)),
  text
);
const RETENTION_PRESETS = new Set(['5', '10', '30', '60', '180', '360', '720', '1440']);
const defaultImagePassword = () => {
  const now = new Date();
  return `${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
};

const setError = (element, message) => {
  element.textContent = message || '';
  element.classList.toggle('hidden', !message);
};

const setButtonLoading = (button, loading, idleKey, loadingKey, enabled = true) => {
  button.disabled = loading || !enabled;
  button.dataset.loading = String(loading);
  button.textContent = t(loading ? loadingKey : idleKey);
};

const copyText = async (text, button, errorElement) => {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    button.textContent = t('copiedButton');
    setTimeout(() => {
      button.textContent = t('copyButton');
    }, 1200);
  } catch {
    setError(errorElement, t('errorClipboardDenied'));
  }
};

const resetShortResult = () => {
  resultUrl.textContent = '';
  resultUrl.removeAttribute('href');
  resultTarget.textContent = '';
  resultCard.classList.add('hidden');
};

const resetImageResult = () => {
  imageResultUrl.textContent = '';
  imageResultUrl.removeAttribute('href');
  imageExpiresAt.textContent = '';
  imageResultCard.classList.add('hidden');
};

const imageErrorFromPayload = (payload) => {
  const code = String(payload?.errorCode || '').trim().toUpperCase();
  const errors = {
    IMAGE_REQUIRED: 'errorSelectImage',
    IMAGE_TOO_LARGE: 'errorImageTooLarge',
    VIDEO_TOO_LARGE: 'errorVideoTooLarge',
    VIDEO_TOO_LONG: 'errorVideoTooLong',
    MEDIA_TOO_LARGE: 'errorMediaTooLarge',
    UNSUPPORTED_IMAGE: 'errorUnsupportedImage',
    UNSUPPORTED_MEDIA: 'errorUnsupportedMedia',
    RETENTION_TOO_LONG: 'errorRetentionTooLong',
    INVALID_PASSWORD: 'errorInvalidImagePassword',
    IMAGE_SHARING_DISABLED: 'errorImageSharingDisabled',
    MEDIA_STORAGE_FAILED: 'errorMediaStorageFailed',
    MEDIA_PERSISTENCE_FAILED: 'errorMediaPersistenceFailed',
    MEDIA_GATEWAY_FAILED: 'errorMediaGatewayFailed'
  };
  if (code === 'VIDEO_TOO_LONG') {
    return format(t('errorVideoTooLong'), { minutes: maxVideoDurationMinutes() });
  }
  if (code === 'MEDIA_GATEWAY_FAILED') {
    return format(t('errorMediaGatewayFailed'), { status: payload?.status || '5xx' });
  }
  return errors[code] ? t(errors[code]) : String(payload?.error || '').trim() || t('errorImageUploadFailed');
};

const maxVideoDurationMinutes = () => Math.max(
  1,
  Math.ceil(Number(imageShareConfig.maxVideoDurationSeconds || 5 * 60) / 60)
);

const isVideoFile = (file) => Boolean(file) && (
  String(file.type || '').toLowerCase().startsWith('video/')
  || /\.(?:mp4|webm)$/i.test(String(file.name || ''))
);

const readVideoDurationSeconds = (file) => new Promise((resolve, reject) => {
  const video = document.createElement('video');
  const objectUrl = URL.createObjectURL(file);
  let settled = false;
  let timeout = null;
  const finish = (error, duration) => {
    if (settled) return;
    settled = true;
    clearTimeout(timeout);
    video.onloadedmetadata = null;
    video.onerror = null;
    video.removeAttribute('src');
    video.load();
    URL.revokeObjectURL(objectUrl);
    if (error) reject(error);
    else resolve(duration);
  };
  timeout = setTimeout(() => finish(new Error('Video metadata timed out.')), 10_000);
  video.preload = 'metadata';
  video.onloadedmetadata = () => {
    const duration = Number(video.duration);
    finish(Number.isFinite(duration) && duration > 0 ? null : new Error('Invalid video duration.'), duration);
  };
  video.onerror = () => finish(new Error('Unable to read video metadata.'));
  video.src = objectUrl;
});

const toLocalDateTimeValue = (timestamp) => {
  const date = new Date(timestamp);
  return new Date(timestamp - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};

const syncExpirationMode = () => {
  const customExpiration = imageRetentionPreset.value === 'custom';
  imageCustomExpirationField.classList.toggle('hidden', !customExpiration);
  imageCustomExpiration.required = customExpiration;
  if (customExpiration && !imageCustomExpiration.value) {
    const defaultMillis = Math.max(1, Number(imageShareConfig.defaultRetentionHours || 1)) * 60 * 60 * 1000;
    imageCustomExpiration.value = toLocalDateTimeValue(Date.now() + defaultMillis);
  }
};

const syncPasswordProtection = () => {
  const protectedImage = imagePasswordProtected.checked;
  imagePasswordField.classList.toggle('is-disabled', !protectedImage);
  imagePassword.disabled = !protectedImage;
  imagePasswordVisibility.disabled = !protectedImage;
  imagePassword.required = protectedImage;
  if (!protectedImage) {
    imagePassword.type = 'password';
  }
  if (protectedImage && !imagePassword.value.trim()) {
    imagePassword.value = defaultImagePassword();
  }
  syncPasswordVisibility();
};

const syncPasswordVisibility = () => {
  const visible = imagePassword.type === 'text';
  imagePasswordVisibility.setAttribute('aria-pressed', String(visible));
  imagePasswordVisibilityText.textContent = t(visible ? 'hidePassword' : 'showPassword');
};

const applyImageConstraints = (resetRetention = false) => {
  const maxRetentionDays = Math.min(365, Math.max(1, Number(imageShareConfig.maxRetentionDays || 365)));
  const maxRetentionMillis = maxRetentionDays * 24 * 60 * 60 * 1000;
  const defaultMinutes = Math.min(
    maxRetentionDays * 24 * 60,
    Math.max(1, Number(imageShareConfig.defaultRetentionHours || 1) * 60)
  );
  const now = Date.now();
  imageCustomExpiration.min = toLocalDateTimeValue(now + 60_000);
  imageCustomExpiration.max = toLocalDateTimeValue(now + maxRetentionMillis);
  if (resetRetention || (!RETENTION_PRESETS.has(imageRetentionPreset.value) && imageRetentionPreset.value !== 'custom')) {
    const presetValue = String(defaultMinutes);
    imageRetentionPreset.value = RETENTION_PRESETS.has(presetValue) ? presetValue : 'custom';
    imageCustomExpiration.value = imageRetentionPreset.value === 'custom'
      ? toLocalDateTimeValue(now + defaultMinutes * 60_000)
      : '';
  }
  textElements.imageLimitHint.textContent = format(t('imageLimitHint'), {
    imageMax: Math.max(1, Number(imageShareConfig.maxFileSizeMb || 20)),
    videoMax: Math.max(1, Number(imageShareConfig.maxVideoFileSizeMb || 100)),
    minutes: maxVideoDurationMinutes()
  });
  textElements.imageRetentionHint.textContent = format(t('imageRetentionHint'), {
    days: maxRetentionDays
  });
  imageShareForm.classList.toggle('is-disabled', !imageShareConfig.enabled);
  setButtonLoading(
    createImageShareButton,
    createImageShareButton.dataset.loading === 'true',
    'createImageShareButton',
    'creatingImageShareButton',
    imageShareConfig.enabled
  );
  syncExpirationMode();
};

const applyLanguage = (lang) => {
  currentLang = I18N[lang] ? lang : DEFAULT_LANG;
  localStorage.setItem(LANG_STORAGE_KEY, currentLang);
  document.documentElement.lang = currentLang;
  document.title = t('pageTitle');
  uiLangSelect.value = currentLang;

  Object.entries(textElements).forEach(([key, element]) => {
    if (key === 'imageLimitHint' || key === 'imageRetentionHint') return;
    element.textContent = t(key);
  });
  Array.from(imageRetentionPreset.options).forEach((option) => {
    const key = option.dataset.i18n;
    if (key) option.textContent = t(key);
  });
  urlInput.placeholder = t('targetUrlPlaceholder');
  codeInput.placeholder = t('customCodePlaceholder');
  setButtonLoading(submitButton, submitButton.dataset.loading === 'true', 'createButton', 'creatingButton');
  copyButton.textContent = t('copyButton');
  copyImageShareButton.textContent = t('copyButton');
  syncPasswordVisibility();
  applyImageConstraints();
};

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const url = urlInput.value.trim();
  const customCode = codeInput.value.trim();
  if (!url) {
    setError(errorText, t('errorEnterUrl'));
    return;
  }

  setError(errorText, '');
  resetShortResult();
  setButtonLoading(submitButton, true, 'createButton', 'creatingButton');
  try {
    const body = { url };
    if (customCode) body.customCode = customCode;
    const response = await fetch('/api/short', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json;charset=UTF-8' },
      body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const code = String(payload?.errorCode || '').toUpperCase();
      setError(errorText, code === 'INVALID_URL_OR_CODE' ? t('errorInvalidUrlOrCode') : String(payload?.error || t('errorCreateFailed')));
      return;
    }
    const shortUrl = String(payload?.shortUrl || '').trim();
    if (!shortUrl) {
      setError(errorText, t('errorMissingShortUrl'));
      return;
    }
    resultUrl.textContent = shortUrl;
    resultUrl.href = shortUrl;
    resultTarget.textContent = String(payload?.targetUrl || url);
    resultCard.classList.remove('hidden');
  } catch (error) {
    setError(errorText, error instanceof Error ? error.message : t('errorRequestFailed'));
  } finally {
    setButtonLoading(submitButton, false, 'createButton', 'creatingButton');
  }
});

imageFile.addEventListener('change', () => {
  imageFileName.textContent = imageFile.files?.[0]?.name || '';
});

imagePasswordProtected.addEventListener('change', syncPasswordProtection);
imagePasswordVisibility.addEventListener('click', () => {
  if (imagePasswordVisibility.disabled) return;
  imagePassword.type = imagePassword.type === 'password' ? 'text' : 'password';
  syncPasswordVisibility();
  imagePassword.focus();
});

imageShareForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const selectedMedia = imageFile.files?.[0];
  if (!selectedMedia) {
    setError(imageErrorText, t('errorSelectImage'));
    return;
  }
  const selectedVideo = isVideoFile(selectedMedia);
  if (selectedVideo) {
    if (selectedMedia.size > Number(imageShareConfig.maxVideoFileSizeBytes || 100 * 1024 * 1024)) {
      setError(imageErrorText, t('errorVideoTooLarge'));
      return;
    }
    try {
      const duration = await readVideoDurationSeconds(selectedMedia);
      if (duration > Number(imageShareConfig.maxVideoDurationSeconds || 5 * 60)) {
        setError(imageErrorText, format(t('errorVideoTooLong'), { minutes: maxVideoDurationMinutes() }));
        return;
      }
    } catch {
      setError(imageErrorText, t('errorVideoMetadata'));
      return;
    }
  } else if (selectedMedia.size > Number(imageShareConfig.maxFileSizeBytes || 20 * 1024 * 1024)) {
    setError(imageErrorText, t('errorImageTooLarge'));
    return;
  }
  const retentionPreset = imageRetentionPreset.value;
  const customExpiration = retentionPreset === 'custom';
  const retentionMinutes = Number(retentionPreset);
  const maxRetentionDays = Math.min(365, Math.max(1, Number(imageShareConfig.maxRetentionDays || 365)));
  const expiresAt = customExpiration ? Date.parse(imageCustomExpiration.value) : 0;
  if (customExpiration) {
    const now = Date.now();
    if (!Number.isFinite(expiresAt) || expiresAt <= now || expiresAt - now > maxRetentionDays * 24 * 60 * 60 * 1000) {
      setError(imageErrorText, format(t('errorInvalidCustomExpiration'), { days: maxRetentionDays }));
      return;
    }
  } else if (!RETENTION_PRESETS.has(retentionPreset) || !Number.isInteger(retentionMinutes)) {
    setError(imageErrorText, t('errorRetentionTooLong'));
    return;
  }
  const passwordProtected = imagePasswordProtected.checked;
  const password = imagePassword.value.trim();
  if (passwordProtected && (password.length < 4 || password.length > 128)) {
    setError(imageErrorText, t('errorInvalidImagePassword'));
    return;
  }

  setError(imageErrorText, '');
  resetImageResult();
  setButtonLoading(createImageShareButton, true, 'createImageShareButton', 'creatingImageShareButton', imageShareConfig.enabled);
  try {
    const formData = new FormData();
    formData.append('image', selectedMedia);
    if (customExpiration) {
      formData.append('expiresAt', String(expiresAt));
    } else {
      formData.append('retentionMinutes', String(retentionMinutes));
    }
    formData.append('passwordProtected', String(passwordProtected));
    if (passwordProtected) formData.append('password', password);
    const response = await fetch('/api/short/image', { method: 'POST', body: formData });
    const responseText = await response.text();
    let payload = {};
    try {
      payload = responseText ? JSON.parse(responseText) : {};
    } catch {
      payload = {};
    }
    if (!response.ok) {
      if (!payload.errorCode && [502, 503, 504].includes(response.status)) {
        payload = { errorCode: 'MEDIA_GATEWAY_FAILED', status: response.status };
      } else if (!payload.errorCode && response.status === 413) {
        payload = { errorCode: 'MEDIA_TOO_LARGE' };
      }
      setError(imageErrorText, imageErrorFromPayload(payload));
      return;
    }
    const shortUrl = String(payload?.shortUrl || '').trim();
    if (!shortUrl) {
      setError(imageErrorText, t('errorMissingShortUrl'));
      return;
    }
    imageResultUrl.textContent = shortUrl;
    imageResultUrl.href = shortUrl;
    const responseExpiresAt = Number(payload?.expiresAt);
    imageExpiresAt.textContent = Number.isFinite(responseExpiresAt)
      ? new Date(responseExpiresAt).toLocaleString(currentLang === 'zh-TW' ? 'zh-TW' : 'en-US')
      : '';
    imageResultCard.classList.remove('hidden');
  } catch (error) {
    setError(imageErrorText, error instanceof Error ? error.message : t('errorImageUploadFailed'));
  } finally {
    setButtonLoading(createImageShareButton, false, 'createImageShareButton', 'creatingImageShareButton', imageShareConfig.enabled);
  }
});

copyButton.addEventListener('click', () => copyText(resultUrl.textContent?.trim(), copyButton, errorText));
copyImageShareButton.addEventListener('click', () => copyText(imageResultUrl.textContent?.trim(), copyImageShareButton, imageErrorText));
uiLangSelect.addEventListener('change', () => applyLanguage(uiLangSelect.value));
imageRetentionPreset.addEventListener('change', syncExpirationMode);

syncPasswordProtection();
applyLanguage(localStorage.getItem(LANG_STORAGE_KEY) || DEFAULT_LANG);
fetch('/api/short/image/config')
  .then((response) => (response.ok ? response.json() : null))
  .then((payload) => {
    if (!payload) return;
    imageShareConfig = { ...imageShareConfig, ...payload };
    applyImageConstraints(true);
  })
  .catch(() => applyImageConstraints());
