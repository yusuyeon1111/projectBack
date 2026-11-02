import { createContext, useContext, useState, ReactNode } from "react";

interface PostContextValue {
  postId: number | null;
  setPostId: (id: number | null) => void;
}

const PostContext = createContext<PostContextValue | undefined>(undefined);

export function PostProvider({ children }: { children: ReactNode }) {
  const [postId, setPostId] = useState<number | null>(null);
  return (
    <PostContext.Provider value={{ postId, setPostId }}>
      {children}
    </PostContext.Provider>
  );
}

export function usePost() {
  return useContext(PostContext);
}
