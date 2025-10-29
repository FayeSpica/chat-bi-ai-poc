/**
 * Cookie工具函数
 */

/**
 * 从cookie中获取指定名称的值
 * @param name cookie名称
 * @returns cookie值，如果不存在则返回空字符串
 */
export const getCookie = (name: string): string => {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) {
    const cookieValue = parts.pop()?.split(';').shift();
    return cookieValue || '';
  }
  return '';
};

/**
 * 设置cookie
 * @param name cookie名称
 * @param value cookie值
 * @param days 过期天数
 */
export const setCookie = (name: string, value: string, days: number = 7): void => {
  const expires = new Date();
  expires.setTime(expires.getTime() + (days * 24 * 60 * 60 * 1000));
  document.cookie = `${name}=${value};expires=${expires.toUTCString()};path=/`;
};

/**
 * 删除cookie
 * @param name cookie名称
 */
export const deleteCookie = (name: string): void => {
  document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/;`;
};

/**
 * 获取login_token
 * @returns login_token值，如果不存在则返回空字符串
 */
export const getLoginToken = (): string => {
  return getCookie('login_token');
};