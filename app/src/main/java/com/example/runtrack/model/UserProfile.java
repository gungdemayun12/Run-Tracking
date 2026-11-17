package com.example.runtrack.model;

public class UserProfile {
    public String uid;
    public String displayName;
    public String email;
    public double weightKg;
    public double heightCm;
    public int age;
    public String gender;
    /** Base64 JPEG (~320px) so the photo follows the profile across devices. */
    public String photoBase64;
    /** Firebase Storage download URL for the profile photo (preferred). */
    public String photoUrl;

    public UserProfile() {}
}
