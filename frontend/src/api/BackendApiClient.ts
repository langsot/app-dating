import type {
  AuthResponse, FeedCardDto, InterestDto, LikeType, MatchDto, PhotoDto, ProfileDto, ProfileForm, RateResult,
} from "../types";

/** Ошибка API с кодом HTTP и сообщением сервера. */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly details: string[] = []) {
    super(message);
  }
}

/** Клиент REST API сервера: хранит токен доступа и преобразует ответы в DTO. */
export class BackendApiClient {
  private token: string | null = null;

  constructor(private readonly baseUrl = "", private readonly fetchImpl: typeof fetch = fetch.bind(globalThis)) {}

  get authorized(): boolean { return this.token !== null; }

  private async request<T>(method: string, path: string, body?: unknown, isForm = false): Promise<T> {
    const headers: Record<string, string> = {};
    if (this.token) headers["Authorization"] = `Bearer ${this.token}`;
    if (body !== undefined && !isForm) headers["Content-Type"] = "application/json";
    let resp: Response;
    try {
      resp = await this.fetchImpl(this.baseUrl + path, {
        method, headers,
        body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
      });
    } catch {
      throw new ApiError(0, "Нет соединения с сервером. Проверьте интернет и повторите попытку.");
    }
    if (resp.status === 204) return undefined as T;
    const text = await resp.text();
    const data = text ? JSON.parse(text) : null;
    if (!resp.ok) throw new ApiError(resp.status, data?.message ?? `Ошибка ${resp.status}`, data?.details ?? []);
    return data as T;
  }

  async authenticate(initData: string): Promise<AuthResponse> {
    const r = await this.request<AuthResponse>("POST", "/api/auth/telegram", { initData });
    this.token = r.token;
    return r;
  }

  /** Возвращает анкету или null, если она еще не создана. */
  async getMyProfile(): Promise<ProfileDto | null> {
    try {
      return await this.request<ProfileDto>("GET", "/api/profile/me");
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }
  }

  saveProfile(f: ProfileForm): Promise<ProfileDto> {
    return this.request<ProfileDto>("PUT", "/api/profile/me", {
      ...f, bio: f.bio || null, city: f.city || null, gender: f.gender || null,
    });
  }

  uploadPhoto(file: Blob): Promise<PhotoDto> {
    const fd = new FormData();
    fd.append("file", file);
    return this.request<PhotoDto>("POST", "/api/profile/me/photos", fd, true);
  }

  deletePhoto(id: number): Promise<void> { return this.request<void>("DELETE", `/api/profile/me/photos/${id}`); }
  setHidden(hidden: boolean): Promise<void> { return this.request<void>("PATCH", "/api/profile/me/visibility", { hidden }); }
  /** Безвозвратное удаление учетной записи со всеми данными. */
  deleteAccount(): Promise<void> { return this.request<void>("DELETE", "/api/profile/me"); }
  getInterests(): Promise<InterestDto[]> { return this.request<InterestDto[]>("GET", "/api/interests"); }
  getFeed(limit = 10): Promise<FeedCardDto[]> { return this.request<FeedCardDto[]>("GET", `/api/feed?limit=${limit}`); }
  rate(userId: number, type: LikeType): Promise<RateResult> {
    return this.request<RateResult>("POST", `/api/feed/${userId}/rate`, { type });
  }
  getMatches(): Promise<MatchDto[]> { return this.request<MatchDto[]>("GET", "/api/matches"); }
  unmatch(id: number): Promise<void> { return this.request<void>("DELETE", `/api/matches/${id}`); }
}
