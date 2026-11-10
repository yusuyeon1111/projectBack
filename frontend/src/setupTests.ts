// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';
import { TextEncoder } from 'util';

// react-router 7이 쓰는 TextEncoder가 jest 27의 jsdom 환경에는 없다
Object.assign(global, { TextEncoder });
