// Типы объектов передачи данных REST API (соответствуют record-классам Dtos на сервере)
export type Gender = "MALE" | "FEMALE";
export type GenderPreference = "MALE" | "FEMALE" | "ANY";
export type LikeType = "LIKE" | "DISLIKE" | "SUPER_LIKE";

export interface AuthResponse { token: string; newUser: boolean; profileComplete: boolean; userId: number; }
export interface PhotoDto { id: number; url: string; position: number; }
export interface ProfileDto {
  userId: number; displayName: string; birthDate: string; age: number; gender: Gender;
  bio: string | null; city: string | null; preferredGender: GenderPreference;
  minAge: number; maxAge: number; photos: PhotoDto[]; interests: string[]; hidden: boolean; complete: boolean;
}
export interface ProfileForm {
  displayName: string; birthDate: string; gender: Gender | ""; bio: string; city: string;
  preferredGender: GenderPreference; minAge: number; maxAge: number; interests: string[];
}
export interface FeedCardDto {
  userId: number; displayName: string; age: number; city: string | null; bio: string | null;
  photoUrls: string[]; interests: string[]; superLikedYou: boolean;
}
export interface MatchDto {
  id: number; partnerId: number; partnerName: string; partnerAge: number | null;
  partnerPhotoUrl: string | null; partnerUsername: string | null; matchedAt: string;
}
export interface RateResult { matched: boolean; matchId: number | null; match: MatchDto | null; }
export interface InterestDto { name: string; category: string; }
export interface SearchFilter { preferredGender: GenderPreference; minAge: number; maxAge: number; }
export type ScreenId = "login" | "editor" | "feed" | "matches" | "deleted";
export type Direction = "left" | "right" | "up" | "none";
