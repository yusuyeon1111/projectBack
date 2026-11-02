// PostContext.js
import { createContext, useContext, useState } from "react";

const PostContext = createContext();

export function PostProvider({ children }) {
  const [postId, setPostId] = useState(null);
  return (
    <PostContext.Provider value={{ postId, setPostId }}>
      {children}
    </PostContext.Provider>
  );
}

export function usePost() {
  return useContext(PostContext);
}
