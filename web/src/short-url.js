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
const imageRetentionHours = document.getElementById('imageRetentionHours');
const imagePasswordProtected = document.getElementById('imagePasswordProtected');
const imagePasswordField = document.getElementById('imagePasswordField');
const imagePassword = document.getElementById('imagePassword');
const createImageShareButton = document.getElementById('createImageShareBtn');
const imageErrorText = document.getElementById('imageErrorText');
const imageResultCard = document.getElementById('imageResultCard');
const imageResultUrl = document.getElementById('imageResultUrl');
const imageExpiresAt = document.getElementById('imageExpiresAt');
const imageShareTitle = document.getElementById('imageShareTitle');
const imageShareSubtitle = document.getElementById('imageShareSubtitle');
const imageFileLabel = document.getElementById('imageFileLabel');
const imageLimitHint = document.getElementById('imageLimitHint');
const imageRetentionLabel = document.getElementById('imageRetentionLabel');
const imageRetentionHint = document.getElementById('imageRetentionHint');
const imagePasswordProtectedLabel = document.getElementById('imagePasswordProtectedLabel');
const imagePasswordLabel = document.getElementById('imagePasswordLabel');
const imagePasswordHint = document.getElementById('imagePasswordHint');
const imageExpiresLabel = document.getElementById('imageExpiresLabel');

const shortUrlTitle = document.getElementById('shortUrlTitle');
const shortUrlSubtitle = document.getElementById('shortUrlSubtitle');
const targetUrlLabel = document.getElementById('targetUrlLabel');
const customCodeLabel = document.getElementById('customCodeLabel');
const resultTitle = document.getElementById('resultTitle');
const resultTargetLabel = document.getElementById('resultTargetLabel');
const uiLangLabel = document.getElementById('uiLangLabel');

if (!form
  || !urlInput
  || !submitButton
  || !resultCard
  || !resultUrl
  || !errorText
  || !copyButton
  || !uiLangSelect
  || !shortUrlTitle
  || !shortUrlSubtitle
  || !targetUrlLabel
  || !customCodeLabel
  || !resultTitle
  || !resultTargetLabel
  || !uiLangLabel
  || !imageShareForm
  || !imageFile
  || !imageRetentionHours
  || !imagePasswordProtected
  || !imagePasswordField
  || !imagePassword
  || !createImageShareButton
  || !imageErrorText
  || !imageResultCard
  || !imageResultUrl
  || !imageExpiresAt
  || !imageShareTitle
  || !imageShareSubtitle
  || !imageFileLabel
  || !imageLimitHint
  || !imageRetentionLabel
  || !imageRetentionHint
  || !imagePasswordProtectedLabel
  || !imagePasswordLabel
  || !imagePasswordHint
  || !imageExpiresLabel) {
  throw new Error('Short URL page is missing required DOM elements.');
}

const LANG_STORAGE_KEY = 'norule.shorturl.ui.lang';
const DEFAULT_LANG = 'zh-TW';
const I18N = {
  'zh-TW': {
    pageTitle: '\u77ed\u7db2\u5740',
    uiLangLabel: '\u8a9e\u8a00',
    title: '\u77ed\u7db2\u5740',
    subtitle: '\u5efa\u7acb\u77ed\u9023\u7d50\u4e26\u7acb\u5373\u5206\u4eab\u3002',
    targetUrlLabel: '\u76ee\u6a19\u7db2\u5740',
    targetUrlPlaceholder: 'https://example.com/path',
    customCodeLabel: '\u81ea\u8a02\u4ee3\u78bc\uff08\u9078\u586b\uff09',
    customCodePlaceholder: 'my-link',
    createButton: '\u5efa\u7acb\u77ed\u7db2\u5740',
    creatingButton: '\u5efa\u7acb\u4e2d...',
    resultTitle: '\u7d50\u679c',
    resultTargetLabel: '\u76ee\u6a19',
    copyButton: '\u8907\u88fd',
    copiedButton: '\u5df2\u8907\u88fd',
    errorEnterUrl: '\u8acb\u8f38\u5165\u7db2\u5740\u3002',
    errorCreateFailed: '\u5efa\u7acb\u77ed\u7db2\u5740\u5931\u6557\u3002',
    errorRequestFailed: '\u8acb\u6c42\u5931\u6557\u3002',
    errorClipboardDenied: '\u526a\u8cbc\u7c3f\u6b0a\u9650\u88ab\u62d2\u7d55\u3002',
    errorInvalidUrlOrCode: '\u7db2\u5740\u6216\u81ea\u8a02\u4ee3\u78bc\u7121\u6548\u3002',
    errorMissingUrl: '\u7f3a\u5c11\u7db2\u5740\u53c3\u6578\u3002',
    errorMethodNotAllowed: '\u4e0d\u652f\u63f4\u7684\u8acb\u6c42\u65b9\u5f0f\u3002',
    errorMissingShortUrl: '\u56de\u61c9\u7f3a\u5c11\u77ed\u7db2\u5740\u6b04\u4f4d\u3002',
    imageShareTitle: '\u5206\u4eab\u5716\u7247',
    imageShareSubtitle: '\u4e0a\u50b3\u5716\u7247\u4e26\u7522\u751f\u77ed\u7db2\u5740\u3002',
    imageFileLabel: '\u5716\u7247\u6a94\u6848',
    imageLimitHint: '\u652f\u63f4 PNG\u3001JPEG\u3001GIF \u8207 WebP\uff0c\u5927\u5c0f\u4e0a\u9650 {max} MB\u3002',
    imageRetentionLabel: '\u4fdd\u5b58\u6642\u9593\uff08\u5c0f\u6642\uff09',
    imageRetentionHint: '\u8acb\u8f38\u5165 1\uff5e{max} \u5c0f\u6642\uff08\u6700\u591a {days} \u5929\uff09\u3002',
    imagePasswordProtectedLabel: '\u4f7f\u7528\u5bc6\u78bc\u4fdd\u8b77',
    imagePasswordLabel: '\u5bc6\u78bc',
    imagePasswordHint: '\u9810\u8a2d\u70ba\u4eca\u5929\u7684\u56db\u4f4d\u6578\u5b57\uff08MMDD\uff09\uff0c\u4e5f\u53ef\u4ee5\u81ea\u884c\u4fee\u6539\u3002',
    createImageShareButton: '\u5efa\u7acb\u5716\u7247\u5206\u4eab\u9023\u7d50',
    creatingImageShareButton: '\u4e0a\u50b3\u4e2d...',
    imageExpiresLabel: '\u5230\u671f\u6642\u9593',
    errorSelectImage: '\u8acb\u9078\u64c7\u5716\u7247\u6a94\u6848\u3002',
    errorImageTooLarge: '\u5716\u7247\u8d85\u904e\u5141\u8a31\u7684\u5927\u5c0f\u3002',
    errorUnsupportedImage: '\u50c5\u652f\u63f4 PNG\u3001JPEG\u3001GIF \u8207 WebP \u5716\u7247\u3002',
    errorRetentionTooLong: '\u4fdd\u5b58\u6642\u9593\u8d85\u904e\u5141\u8a31\u7bc4\u570d\u3002',
    errorInvalidImagePassword: '\u5bc6\u78bc\u9700\u70ba 4\uff5e128 \u500b\u5b57\u5143\u3002',
    errorImageSharingDisabled: '\u5716\u7247\u5206\u4eab\u529f\u80fd\u672a\u958b\u555f\u3002',
    errorImageUploadFailed: '\u5716\u7247\u4e0a\u50b3\u5931\u6557\u3002'
  },
  en: {
    pageTitle: 'Short URL',
    uiLangLabel: 'Language',
    title: 'Short URL',
    subtitle: 'Create a short link and share it instantly.',
    targetUrlLabel: 'Target URL',
    targetUrlPlaceholder: 'https://example.com/path',
    customCodeLabel: 'Custom Code (Optional)',
    customCodePlaceholder: 'my-link',
    createButton: 'Create Short URL',
    creatingButton: 'Creating...',
    resultTitle: 'Result',
    resultTargetLabel: 'Target',
    copyButton: 'Copy',
    copiedButton: 'Copied',
    errorEnterUrl: 'Please enter a URL.',
    errorCreateFailed: 'Failed to create short URL.',
    errorRequestFailed: 'Request failed.',
    errorClipboardDenied: 'Clipboard permission denied.',
    errorInvalidUrlOrCode: 'Invalid URL or custom code.',
    errorMissingUrl: 'Missing URL parameter.',
    errorMethodNotAllowed: 'Method not allowed.',
    errorMissingShortUrl: 'Response missing short URL field.',
    imageShareTitle: 'Image Share',
    imageShareSubtitle: 'Upload an image and get a short sharing link.',
    imageFileLabel: 'Image file',
    imageLimitHint: 'PNG, JPEG, GIF, and WebP up to {max} MB.',
    imageRetentionLabel: 'Retention (hours)',
    imageRetentionHint: 'Enter 1–{max} hours (up to {days} days).',
    imagePasswordProtectedLabel: 'Protect with password',
    imagePasswordLabel: 'Password',
    imagePasswordHint: 'Defaults to today’s four digits (MMDD); you can change it.',
    createImageShareButton: 'Create Image Share Link',
    creatingImageShareButton: 'Uploading...',
    imageExpiresLabel: 'Expires',
    errorSelectImage: 'Select an image file.',
    errorImageTooLarge: 'The image exceeds the allowed size.',
    errorUnsupportedImage: 'Only PNG, JPEG, GIF, and WebP images are supported.',
    errorRetentionTooLong: 'Retention is outside the allowed range.',
    errorInvalidImagePassword: 'Password must contain 4 to 128 characters.',
    errorImageSharingDisabled: 'Image sharing is disabled.',
    errorImageUploadFailed: 'Image upload failed.'
  }
};

let currentLang = DEFAULT_LANG;
let imageShareConfig = {
  enabled: true,
  defaultRetentionHours: 1,
  maxRetentionDays: 365,
  maxFileSizeBytes: 20 * 1024 * 1024,
  maxFileSizeMb: 20
};

const resolveLang = (value) => {
  if (!value) return DEFAULT_LANG;
  return I18N[value] ? value : DEFAULT_LANG;
};

const t = (key) => I18N[currentLang]?.[key] ?? I18N[DEFAULT_LANG][key] ?? key;

const format = (text, values) => Object.entries(values).reduce(
  (result, [key, value]) => result.replaceAll(`{${key}}`, String(value)),
  text
);

const defaultImagePassword = () => {
  const now = new Date();
  return `${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
};

const setImageError = (message) => {
  imageErrorText.textContent = message || '';
  imageErrorText.classList.toggle('hidden', !message);
};

const resetImageResult = () => {
  imageResultUrl.textContent = '';
  imageResultUrl.removeAttribute('href');
  imageExpiresAt.textContent = '';
  imageResultCard.classList.add('hidden');
};

const setImageLoading = (loading) => {
  createImageShareButton.dataset.loading = String(loading);
  createImageShareButton.disabled = loading || !imageShareConfig.enabled;
  createImageShareButton.textContent = t(loading ? 'creatingImageShareButton' : 'createImageShareButton');
};

const imageErrorFromPayload = (payload) => {
  const code = String(payload?.errorCode || '').trim().toUpperCase();
  const errors = {
    IMAGE_REQUIRED: 'errorSelectImage',
    IMAGE_TOO_LARGE: 'errorImageTooLarge',
    UNSUPPORTED_IMAGE: 'errorUnsupportedImage',
    RETENTION_TOO_LONG: 'errorRetentionTooLong',
    INVALID_PASSWORD: 'errorInvalidImagePassword',
    IMAGE_SHARING_DISABLED: 'errorImageSharingDisabled'
  };
  return errors[code] ? t(errors[code]) : String(payload?.error || '').trim() || t('errorImageUploadFailed');
};

const applyImageConstraints = () => {
  const maxHours = Math.max(1, Number(imageShareConfig.maxRetentionDays || 365) * 24);
  const defaultHours = Math.min(maxHours, Math.max(1, Number(imageShareConfig.defaultRetentionHours || 1)));
  const maxFileSizeMb = Math.max(1, Number(imageShareConfig.maxFileSizeMb || 20));
  imageRetentionHours.min = '1';
  imageRetentionHours.max = String(maxHours);
  imageRetentionHours.value = String(defaultHours);
  imageLimitHint.textContent = format(t('imageLimitHint'), { max: maxFileSizeMb });
  imageRetentionHint.textContent = format(t('imageRetentionHint'), {
    max: maxHours,
    days: Math.max(1, Number(imageShareConfig.maxRetentionDays || 365))
  });
  imageShareForm.classList.toggle('opacity-50', !imageShareConfig.enabled);
  setImageLoading(createImageShareButton.dataset.loading === 'true');
};

const showImageResult = (shortUrl, expiresAt) => {
  imageResultUrl.textContent = shortUrl;
  imageResultUrl.href = shortUrl;
  const timestamp = Number(expiresAt);
  imageExpiresAt.textContent = Number.isFinite(timestamp)
    ? new Date(timestamp).toLocaleString(currentLang === 'zh-TW' ? 'zh-TW' : 'en-US')
    : '';
  imageResultCard.classList.remove('hidden');
};

const setError = (message) => {
  errorText.textContent = message || '';
  errorText.classList.toggle('hidden', !message);
};

const resetResult = () => {
  resultUrl.textContent = '';
  resultUrl.removeAttribute('href');
  resultTarget.textContent = '';
  resultCard.classList.add('hidden');
};

const setLoading = (loading) => {
  submitButton.disabled = loading;
  submitButton.textContent = loading ? t('creatingButton') : t('createButton');
};

const mapBackendError = (message) => {
  const raw = String(message || '').trim();
  const key = raw.toLowerCase();
  if (!raw) return t('errorCreateFailed');
  if (key.includes('invalid url or code')) return t('errorInvalidUrlOrCode');
  if (key.includes('missing url')) return t('errorMissingUrl');
  if (key.includes('method not allowed')) return t('errorMethodNotAllowed');
  return raw;
};

const mapBackendPayloadError = (payload) => {
  const code = String(payload?.errorCode || '').trim().toUpperCase();
  if (code === 'INVALID_URL_OR_CODE') return t('errorInvalidUrlOrCode');
  if (code === 'MISSING_URL') return t('errorMissingUrl');
  if (code === 'METHOD_NOT_ALLOWED') return t('errorMethodNotAllowed');
  return mapBackendError(payload?.error);
};

const resolveShortUrlFields = (payload) => {
  const shortUrl = String(payload?.shortUrl || payload?.url || '').trim();
  const targetUrl = String(payload?.targetUrl || payload?.target || '').trim();
  return { shortUrl, targetUrl };
};

const applyLanguage = (lang) => {
  currentLang = resolveLang(lang);
  localStorage.setItem(LANG_STORAGE_KEY, currentLang);
  document.documentElement.lang = currentLang;
  document.title = t('pageTitle');

  uiLangSelect.value = currentLang;
  uiLangLabel.textContent = t('uiLangLabel');
  shortUrlTitle.textContent = t('title');
  shortUrlSubtitle.textContent = t('subtitle');
  targetUrlLabel.textContent = t('targetUrlLabel');
  customCodeLabel.textContent = t('customCodeLabel');
  resultTitle.textContent = t('resultTitle');
  resultTargetLabel.textContent = t('resultTargetLabel');

  imageShareTitle.textContent = t('imageShareTitle');
  imageShareSubtitle.textContent = t('imageShareSubtitle');
  imageFileLabel.textContent = t('imageFileLabel');
  imageRetentionLabel.textContent = t('imageRetentionLabel');
  imagePasswordProtectedLabel.textContent = t('imagePasswordProtectedLabel');
  imagePasswordLabel.textContent = t('imagePasswordLabel');
  imagePasswordHint.textContent = t('imagePasswordHint');
  imageExpiresLabel.textContent = t('imageExpiresLabel');

  urlInput.placeholder = t('targetUrlPlaceholder');
  codeInput.placeholder = t('customCodePlaceholder');

  copyButton.textContent = t('copyButton');
  setLoading(submitButton.disabled);
  applyImageConstraints();
};

const showResult = (shortUrl, targetUrl) => {
  resultUrl.textContent = shortUrl;
  resultUrl.href = shortUrl;
  resultTarget.textContent = targetUrl;
  resultCard.classList.remove('hidden');
};

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const url = urlInput.value.trim();
  const code = codeInput ? codeInput.value.trim() : '';

  if (!url) {
    setError(t('errorEnterUrl'));
    return;
  }

  setError('');
  resetResult();
  setLoading(true);

  try {
    const body = {
      url
    };
    if (code) {
      body.customCode = code;
    }

    const response = await fetch('/api/short', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8'
      },
      body: JSON.stringify(body)
    });

    let payload = null;
    try {
      payload = await response.json();
    } catch {
      payload = null;
    }

    const resolved = resolveShortUrlFields(payload || {});
    if (!response.ok) {
      setError(mapBackendPayloadError(payload || {}));
      return;
    }
    if (!resolved.shortUrl) {
      setError(t('errorMissingShortUrl'));
      return;
    }

    showResult(resolved.shortUrl, resolved.targetUrl || url);
  } catch (error) {
    setError(error instanceof Error ? error.message : t('errorRequestFailed'));
  } finally {
    setLoading(false);
  }
});

imagePasswordProtected.addEventListener('change', () => {
  const protectedImage = imagePasswordProtected.checked;
  imagePasswordField.classList.toggle('hidden', !protectedImage);
  imagePassword.required = protectedImage;
  if (protectedImage && !imagePassword.value.trim()) {
    imagePassword.value = defaultImagePassword();
  }
});

imageShareForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const selectedImage = imageFile.files?.[0];
  if (!selectedImage) {
    setImageError(t('errorSelectImage'));
    return;
  }
  if (selectedImage.size > Number(imageShareConfig.maxFileSizeBytes || 20 * 1024 * 1024)) {
    setImageError(t('errorImageTooLarge'));
    return;
  }

  const retentionHours = Number(imageRetentionHours.value);
  const maxHours = Number(imageShareConfig.maxRetentionDays || 365) * 24;
  if (!Number.isInteger(retentionHours) || retentionHours < 1 || retentionHours > maxHours) {
    setImageError(t('errorRetentionTooLong'));
    return;
  }

  const passwordProtected = imagePasswordProtected.checked;
  const password = imagePassword.value.trim();
  if (passwordProtected && (password.length < 4 || password.length > 128)) {
    setImageError(t('errorInvalidImagePassword'));
    return;
  }

  setImageError('');
  resetImageResult();
  setImageLoading(true);
  try {
    const formData = new FormData();
    formData.append('image', selectedImage);
    formData.append('retentionHours', String(retentionHours));
    formData.append('passwordProtected', String(passwordProtected));
    if (passwordProtected) {
      formData.append('password', password);
    }

    const response = await fetch('/api/short/image', {
      method: 'POST',
      body: formData
    });
    let payload = null;
    try {
      payload = await response.json();
    } catch {
      payload = null;
    }
    if (!response.ok) {
      setImageError(imageErrorFromPayload(payload || {}));
      return;
    }
    const shortUrl = String(payload?.shortUrl || '').trim();
    if (!shortUrl) {
      setImageError(t('errorMissingShortUrl'));
      return;
    }
    showImageResult(shortUrl, payload?.expiresAt);
  } catch (error) {
    setImageError(error instanceof Error ? error.message : t('errorImageUploadFailed'));
  } finally {
    setImageLoading(false);
  }
});

copyButton.addEventListener('click', async () => {
  const text = resultUrl.textContent?.trim();
  if (!text) {
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    copyButton.textContent = t('copiedButton');
    setTimeout(() => {
      copyButton.textContent = t('copyButton');
    }, 1200);
  } catch {
    setError(t('errorClipboardDenied'));
  }
});

uiLangSelect.addEventListener('change', () => {
  applyLanguage(uiLangSelect.value);
});

applyLanguage(resolveLang(localStorage.getItem(LANG_STORAGE_KEY) || DEFAULT_LANG));

fetch('/api/short/image/config')
  .then(async (response) => (response.ok ? response.json() : null))
  .then((payload) => {
    if (!payload) return;
    imageShareConfig = {
      ...imageShareConfig,
      ...payload
    };
    applyImageConstraints();
  })
  .catch(() => {
    applyImageConstraints();
  });
