import type { MatchDto } from "../types";

export function MatchPopup({ match, myPhoto, onWrite, onClose }:
  { match: MatchDto; myPhoto: string | null; onWrite: () => void; onClose: () => void }) {
  return (
    <div className="overlay" data-testid="match-popup">
      <div className="pop">
        <h2>Это мэтч!</h2>
        <div className="pair">
          <div className="avatar" style={{ backgroundImage: myPhoto ? `url(${myPhoto})` : undefined }} />
          <div className="avatar" style={{ backgroundImage: match.partnerPhotoUrl ? `url(${match.partnerPhotoUrl})` : undefined }} />
        </div>
        <p>Вы понравились друг другу с {match.partnerName}</p>
        <button className="btn wide" onClick={onWrite}>Написать в Telegram</button>
        <button className="link" onClick={onClose}>Продолжить просмотр</button>
      </div>
    </div>
  );
}
