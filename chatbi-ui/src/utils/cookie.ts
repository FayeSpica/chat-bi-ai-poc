/**
 * Cookie工具函数
 */

import { UserToken } from '../types';

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
 * 获取login_token（base64编码的UserToken JSON）
 * @returns base64编码的UserToken JSON字符串，如果不存在则返回空字符串
 */
export const getLoginToken = (): string => {
  return getCookie('login_token');
};

/**
 * 设置login_token（将UserToken序列化为JSON并进行base64编码）
 * @param userToken UserToken对象
 * @param days cookie过期天数
 */
export const setLoginToken = (userToken: UserToken, days: number = 7): void => {
  // 将UserToken序列化为JSON
  const jsonString = JSON.stringify(userToken);
  // Base64编码
  const base64Token = btoa(unescape(encodeURIComponent(jsonString)));
  // 保存到cookie
  console.log('setLoginToken', base64Token);
  setCookie('login_token', base64Token, days);
};

/**
 * 从cookie中解析UserToken
 * @returns UserToken对象，如果解析失败则返回null
 */
export const parseLoginToken = (): UserToken | null => {
  const base64Token = getLoginToken();
  if (!base64Token) {
    return null;
  }
  
  try {
    // Base64解码
    const jsonString = decodeURIComponent(escape(atob(base64Token)));
    // JSON解析
    return JSON.parse(jsonString) as UserToken;
  } catch (error) {
    console.error('Failed to parse login token:', error);
    return null;
  }
};