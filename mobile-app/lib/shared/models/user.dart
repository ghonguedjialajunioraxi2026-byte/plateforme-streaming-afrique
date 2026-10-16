/// Utilisateur connecté — miroir de LoginResponse / MeResponse.
class FlickUser {
  FlickUser({
    required this.id,
    required this.email,
    required this.displayName,
    required this.role,
    this.username,
  });

  final int id;
  final String email;
  final String displayName;
  final String role;
  final String? username;

  bool get isCreator =>
      role == 'CREATOR' || role == 'MODERATOR' || role == 'ADMIN' || role == 'SUPER_ADMIN';
  bool get isStaff =>
      role == 'MODERATOR' || role == 'ADMIN' || role == 'SUPER_ADMIN';

  factory FlickUser.fromJson(Map<String, dynamic> json) => FlickUser(
        id: json['userId'] is int
            ? json['userId'] as int
            : int.tryParse('${json['userId'] ?? json['id']}') ?? 0,
        email: (json['email'] ?? '').toString(),
        displayName: (json['displayName'] ?? json['username'] ?? '').toString(),
        role: (json['role'] ?? 'USER').toString(),
        username: json['username']?.toString(),
      );
}

/// Profil public — miroir de ProfileDto / PublicProfileDto.
class UserProfile {
  UserProfile({
    this.username,
    this.displayName,
    this.bio,
    this.avatarUrl,
    this.followers = 0,
    this.following = 0,
  });

  final String? username;
  final String? displayName;
  final String? bio;
  final String? avatarUrl;
  final int followers;
  final int following;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        username: json['username']?.toString(),
        displayName:
            (json['displayName'] ?? json['username'])?.toString(),
        bio: json['bio']?.toString(),
        avatarUrl: json['avatarUrl']?.toString(),
        followers: _i(json['followers'] ?? json['followersCount']),
        following: _i(json['following'] ?? json['followingCount']),
      );

  static int _i(dynamic v) => v is int ? v : int.tryParse('$v') ?? 0;
}
