import { test, expect } from '@playwright/test';

test('chat page renders with input and send button', async ({ page }) => {
  await page.goto('http://localhost:5173');
  await expect(page).toHaveTitle(/PingpongSmart/);

  const textarea = page.locator('textarea');
  const sendBtn = page.locator('button:has-text("发送")');
  await expect(textarea).toBeVisible();
  await expect(sendBtn).toBeVisible();
});

test('input disabled during loading', async ({ page }) => {
  await page.goto('http://localhost:5173');

  const textarea = page.locator('textarea');
  const sendBtn = page.locator('button:has-text("发送")');

  await expect(textarea).toBeEnabled();
  await expect(sendBtn).toBeDisabled();

  await textarea.fill('hello');
  await expect(sendBtn).toBeEnabled();
  await sendBtn.click();

  await expect(textarea).toBeDisabled();
  await expect(sendBtn).toBeDisabled();
});

test('end-to-end: send message and receive streaming response', async ({ page }) => {
  await page.goto('http://localhost:5173');

  const textarea = page.locator('textarea');
  const sendBtn = page.locator('button:has-text("发送")');

  await textarea.fill('test message');
  await expect(sendBtn).toBeEnabled();
  await sendBtn.click();
  await expect(textarea).toBeDisabled();

  await page.waitForTimeout(25000);

  const bubbles = page.locator('.bubble-content');
  await expect(bubbles).toHaveCount(2); // user + assistant

  const lastBubble = bubbles.last();
  const content = await lastBubble.textContent();
  expect(content.length).toBeGreaterThan(0);
});
