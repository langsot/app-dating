import { useMemo, useState } from "react";
import { BackendApiClient } from "./api/BackendApiClient";
import { TelegramWebAppAdapter } from "./telegram/TelegramWebAppAdapter";
import type { AuthResponse, MatchDto, ProfileDto, ScreenId } from "./types";
import { LoginScreen } from "./screens/LoginScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import { ProfileEditorScreen } from "./screens/ProfileEditorScreen";
import { FeedScreen } from "./screens/FeedScreen";
import { MatchListScreen, chatLink } from "./screens/MatchListScreen";
import { MatchPopup } from "./components/MatchPopup";
import { TabBar } from "./components/TabBar";
import { setTelemetryUser } from "./telemetry";

/** Корневой компонент: хранит сессию и переключает экраны. */
export function App() {
  const api = useMemo(() => new BackendApiClient(), []);
  const tg = useMemo(() => new TelegramWebAppAdapter(), []);
  const [screen, setScreen] = useState<ScreenId>("login");
  const [profile, setProfile] = useState<ProfileDto | null>(null);
  const [match, setMatch] = useState<MatchDto | null>(null);

  async function onAuthorized(r: AuthResponse) {
    setTelemetryUser(r.userId);
    const p = await api.getMyProfile();
    setProfile(p);
    setScreen(p && p.complete ? "feed" : "onboarding");
  }

  function onSaved(p: ProfileDto) {
    setProfile(p);
  }

  const canBrowse = !!profile?.complete;
  return (
    <div className="app">
      <main>
        {screen === "login" && <LoginScreen api={api} tg={tg} onAuthorized={onAuthorized} />}
        {screen === "onboarding" && <OnboardingScreen api={api} tg={tg} profile={profile} onSaved={onSaved} onDone={() => setScreen("feed")} />}
        {screen === "editor" && profile && <ProfileEditorScreen api={api} tg={tg} profile={profile} onSaved={onSaved}
                                                      onDeleted={() => { setProfile(null); setScreen("deleted"); }} />}
        {screen === "deleted" && (
          <div className="center-screen" data-testid="deleted">
            <div className="logo">♥</div>
            <h1>Учетная запись удалена</h1>
            <p>Анкета, фотографии и мэтчи удалены безвозвратно.</p>
          </div>
        )}
        {screen === "feed" && profile && canBrowse &&
          <FeedScreen api={api} tg={tg} profile={profile} onProfileChanged={setProfile} onMatch={setMatch} />}
        {screen === "feed" && profile && !canBrowse && (
          <div className="center-screen" data-testid="feed-locked">
            <div className="logo">♥</div>
            <h1>Добавьте фотографию</h1>
            <p>Лента откроется, когда в анкете будет хотя бы одно фото.</p>
            <button className="btn wide" onClick={() => setScreen("editor")} data-testid="feed-add-photo">Перейти к анкете</button>
          </div>
        )}
        {screen === "matches" && <MatchListScreen api={api} tg={tg} />}
      </main>
      {screen !== "login" && screen !== "onboarding" && profile && (
        <TabBar current={screen} onSelect={setScreen} />
      )}
      {match && (
        <MatchPopup match={match} myPhoto={profile?.photos[0]?.url ?? null}
                    onClose={() => setMatch(null)}
                    onWrite={() => { const l = chatLink(match); if (l) tg.openTelegramLink(l); setMatch(null); }} />
      )}
    </div>
  );
}
