package io.github.matijaradovic.spona.common.error;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.matijaradovic.spona.common.web.ProblemTypes;

public enum ErrorCode {

    BAD_CREDENTIALS("auth.badCredentials", "Wrong username or password."),
    SESSION_EXPIRED("auth.sessionExpired", "Session not valid. Sign in again."),
    SIGN_IN_REQUIRED("auth.signInRequired", "Sign in to continue."),
    EMAIL_NOT_VERIFIED("auth.emailNotVerified", "Verify your email address to continue.",
            ProblemTypes.EMAIL_NOT_VERIFIED),
    FORBIDDEN("auth.forbidden", "You do not have sufficient permissions."),
    DEAD_TOKEN("auth.deadToken", "Link is invalid or has expired."),

    USERNAME_RESERVED("user.usernameReserved", "That username is reserved.", ProblemTypes.HANDLE_RESERVED),
    USERNAME_TAKEN("user.usernameTaken", "That username is taken.", ProblemTypes.USERNAME_TAKEN),
    EMAIL_TAKEN("user.emailTaken", "That email address is already registered.", ProblemTypes.EMAIL_TAKEN),
    EMAIL_UNCHANGED("user.emailUnchanged", "That is already your email address."),
    NOT_YOUR_ACCOUNT("user.notYourAccount", "This is not your account."),
    WRONG_PASSWORD("user.wrongPassword", "Wrong password."),

    ACTOR_NOT_FOUND("actor.notFound", "Actor not found."),
    PROFILE_NOT_FOUND("actor.profileNotFound", "Profile not found."),
    NOT_PUBLISHABLE_AS("actor.notPublishableAs", "You may not publish as this actor."),
    UNKNOWN_ACTOR_TYPE("actor.unknownType", "Type must be user or org."),

    ORGANIZATION_NOT_FOUND("organization.notFound", "Organization not found."),
    ORGANIZATION_NOT_A_MEMBER("organization.notAMember", "You are not a member of this organization."),
    ORGANIZATION_NOT_AN_ADMIN("organization.notAnAdmin", "Only the owner or an admin may change this organization."),
    NOT_THE_OWNER("organization.notTheOwner", "Only the owner may do this."),
    SLUG_RESERVED("organization.slugReserved", "That slug is reserved.", ProblemTypes.HANDLE_RESERVED),
    SLUG_TAKEN("organization.slugTaken", "That slug is taken.", ProblemTypes.SLUG_TAKEN),
    SLUG_CONFIRM_MISMATCH("organization.slugConfirmMismatch", "The slug you typed does not match."),
    NEW_OWNER_NOT_ADMIN("organization.newOwnerNotAdmin", "Ownership passes only to an admin of this organization."),

    MEMBER_NOT_FOUND("member.notFound", "Member not found."),
    MEMBER_NOT_A_MEMBER("member.notAMember", "You are not a member of this organization."),
    MEMBER_NOT_AN_ADMIN("member.notAnAdmin", "Only the owner or an admin may change members."),
    ROLE_NOT_ASSIGNABLE("member.roleNotAssignable", "You may not assign this role."),
    ROLE_OUT_OF_REACH("member.roleOutOfReach", "You may not change a member with this role."),
    OWN_MEMBERSHIP("member.notYourself", "You cannot change or remove your own membership."),
    MEMBER_OWNER_BY_TRANSFER_ONLY("member.ownerByTransferOnly", "Ownership moves only by transfer."),
    ALREADY_MEMBER("member.alreadyMember", "This user is already a member.", ProblemTypes.ALREADY_MEMBER),
    ALREADY_MEMBER_SELF("member.alreadyMemberSelf", "You are already a member of this organization.",
            ProblemTypes.ALREADY_MEMBER),
    LAST_OWNER_LEAVE("member.lastOwnerLeave", "Transfer ownership before leaving the organization.",
            ProblemTypes.LAST_OWNER),
    LAST_OWNER_DEMOTE("member.lastOwnerDemote", "The owner keeps their role until ownership is transferred.",
            ProblemTypes.LAST_OWNER),

    INVITATION_NOT_FOUND("invitation.notFound", "Invitation not found."),
    INVITATION_NOT_AN_ADMIN("invitation.notAnAdmin", "Only the owner or an admin may manage invitations."),
    INVITATION_ROLE_TOO_HIGH("invitation.roleTooHigh", "You may not invite someone at this role."),
    INVITATION_OWNER_BY_TRANSFER_ONLY("invitation.ownerByTransferOnly", "Ownership moves only by transfer."),
    NO_SUCH_USERNAME("invitation.noSuchUsername", "No user has this username."),
    INVITATION_ALREADY_WAITING("invitation.alreadyWaiting", "This user already has a waiting invitation."),
    INVITATION_NOT_WAITING("invitation.notWaitingCancel", "Only a waiting invitation can be canceled."),
    INVITATION_NOT_DECLINED("invitation.notDeclinedDismiss", "Only a declined invitation can be dismissed."),
    INVITATION_NOT_YOURS("invitation.notYours", "This invitation is addressed to someone else.",
            ProblemTypes.INVITATION_NOT_YOURS),
    INVITATION_NO_LONGER_WAITING("invitation.noLongerWaiting", "This invitation is no longer waiting."),

    POST_NOT_FOUND("post.notFound", "Post not found."),
    POST_NOT_EDITABLE("post.notEditable", "You may not edit this post."),
    POST_NOT_DELETABLE("post.notDeletable", "You may not delete this post."),
    POST_CONTENT_REQUIRED("post.contentRequired", "Content is required."),
    POST_CONTENT_TOO_LONG("post.contentTooLong", "At most %d characters.", "max"),

    COMMENT_NOT_FOUND("comment.notFound", "Comment not found."),
    COMMENT_NOT_EDITABLE("comment.notEditable", "You may not edit this comment."),
    COMMENT_NOT_DELETABLE("comment.notDeletable", "You may not delete this comment."),
    COMMENT_PARENT_NOT_FOUND("comment.parentNotFound", "No such comment on this post."),
    COMMENT_REPLY_TO_REPLY("comment.replyToReply", "A reply cannot be replied to."),
    COMMENT_CONTENT_REQUIRED("comment.contentRequired", "Content is required."),
    COMMENT_CONTENT_TOO_LONG("comment.contentTooLong", "At most %d characters.", "max"),
    UNKNOWN_COMMENT_SORT("comment.unknownSort", "Sort must be top, new or old."),

    UNKNOWN_REACTION_TARGET("reaction.unknownTargetType", "Unknown reaction target type."),
    EMOJI_NOT_IN_PALETTE("reaction.emojiNotInPalette", "Emoji must be one of the reaction palette."),

    SELF_FOLLOW("follow.notYourself", "You cannot follow yourself."),
    ORGANIZATION_FOLLOWS_NOBODY("follow.organizationFollowsNobody", "An organization follows nobody."),

    PRIVACY_NOT_YOURS("privacy.notYours", "You may not change these privacy settings."),

    UNKNOWN_ACTIVITY_KIND("activity.unknownKind", "Kind must be all, posts or comments."),

    JOB_NOT_FOUND("job.notFound", "Job not found."),
    JOB_NOT_PUBLISHABLE_AS("job.notPublishableAs", "You may not publish job postings as this actor."),
    JOB_NOT_EDITABLE("job.notEditable", "You may not edit this posting."),
    JOB_STATUS_NOT_CHANGEABLE("job.statusNotChangeable", "You may not change this posting's status."),
    JOB_NOT_DELETABLE("job.notDeletable", "You may not delete this posting."),
    JOB_STILL_DRAFT("job.stillDraft", "This posting is still a draft. Publish it before it can take applications."),
    JOB_CLOSED("job.closed", "This posting is closed and takes no more applications."),
    JOB_NOT_CV_APPLY("job.notCvApply", "This posting is not applied to with a CV."),
    JOB_APPLICATION_TYPE_FIXED("job.applicationTypeFixed", "The way to apply is fixed once a posting is published."),
    JOB_BAD_STATUS_TRANSITION("job.badStatusTransition", "A posting cannot move from %s to %s.", "from", "to"),
    JOB_STATUS_NOT_CREATABLE("job.statusNotCreatable", "A posting is created as a draft or open."),
    JOB_STATUS_REQUIRED("job.statusRequired", "At least one status is required."),
    JOB_MANAGER_ONLY_QUERY("job.managerOnlyQuery", "Status and sort are only for whoever manages these postings."),
    JOB_SEARCH_TOO_LONG("job.searchTooLong", "A search is at most %d characters.", "max"),
    UNKNOWN_JOB_ROLE("job.unknownRole", "Unknown role."),
    UNKNOWN_JOB_POSTED("job.unknownPosted", "Posted must be 24h, week or month."),
    UNKNOWN_JOB_SORT("job.unknownSort", "Sort must be date_desc or date_asc."),
    UNKNOWN_POSTINGS_SORT("job.unknownPostingsSort",
            "Sort must be created_desc, created_asc, updated_desc or updated_asc."),
    JOB_SALARY_FILTER_INVALID("job.salaryFilterInvalid", "Salary must be 1."),
    UNKNOWN_JOB_FILTER_VALUE("job.unknownFilterValue", "Unknown %s value.", "field"),
    JOB_LOCATION_TOO_LONG("job.locationTooLong", "At most %d characters.", "max"),
    JOB_KEYWORDS_TOO_MANY("job.keywordsTooMany", "At most %d keywords.", "max"),
    JOB_KEYWORD_LENGTH("job.keywordLength", "Each keyword is 1 to %d characters.", "max"),
    JOB_SALARY_MAX_BELOW_MIN("job.salaryMaxBelowMin", "Must not be below the minimum."),
    JOB_EXTERNAL_URL_TOO_LONG("job.externalUrlTooLong", "At most %d characters.", "max"),
    JOB_EXTERNAL_URL_NOT_WEB("job.externalUrlNotWeb", "An http or https address is required."),
    JOB_INCOMPLETE("job.incompleteToPublish", "The posting is not complete enough to publish."),
    JOB_TITLE_REQUIRED("job.titleRequiredToPublish", "A title is required to publish."),
    JOB_DESCRIPTION_REQUIRED("job.descriptionRequiredToPublish", "A description is required to publish."),
    JOB_CATEGORY_REQUIRED("job.categoryRequiredToPublish", "A category is required to publish."),
    JOB_TYPE_REQUIRED("job.jobTypeRequiredToPublish", "A job type is required to publish."),
    JOB_WORKPLACE_TYPE_REQUIRED("job.workplaceTypeRequiredToPublish", "A workplace type is required to publish."),
    JOB_LOCATION_REQUIRED("job.locationRequiredToPublish", "A location is required to publish."),
    JOB_CONTACT_REQUIRED("job.contactInformationRequiredToPublish", "At least one contact is required to publish."),
    JOB_EXTERNAL_URL_REQUIRED("job.externalUrlRequiredToPublish", "An address to apply at is required to publish."),

    APPLICATIONS_NOT_READABLE("application.notReadable", "You may not read this posting's applications."),
    CV_REQUIRED("application.cvRequired", "Attach your CV as a PDF file."),
    CV_UNREADABLE("application.cvUnreadable", "Your CV could not be read. Try sending it again."),
    CV_NOT_PDF("application.cvNotPdf", "Your CV has to be a PDF file."),

    AVATAR_NOT_PNG_DATA_URL("avatar.notPngDataUrl", "The avatar must be a PNG data URL."),
    AVATAR_NOT_BASE64("avatar.notBase64", "The avatar is not valid base64."),
    AVATAR_NOT_PNG("avatar.notPng", "The avatar is not a PNG."),
    AVATAR_WRONG_DIMENSIONS("avatar.wrongDimensions", "The avatar must be exactly %d by %d pixels.",
            "width", "height"),
    AVATAR_CORRUPT_PNG("avatar.corruptPng", "The avatar is not a valid PNG."),
    AVATAR_TOO_LARGE("avatar.tooLarge", "At most %d KB.", "max"),

    VALUE_TAKEN("common.valueTaken", "That value is already taken."),
    CONFLICT("common.conflict", "The request conflicts with data that already exists."),

    VALIDATION_FAILED("validation.failed", "Validation failed."),
    VALIDATION_INVALID("validation.invalid", "Invalid value."),
    VALIDATION_NOT_BLANK("validation.notBlank", "A value is required."),
    VALIDATION_NOT_NULL("validation.notNull", "A value is required."),
    VALIDATION_SIZE("validation.size", "Between %d and %d characters.", "min", "max"),
    VALIDATION_EMAIL("validation.email", "That is not a valid email address."),
    VALIDATION_PATTERN("validation.pattern", "Invalid format."),
    VALIDATION_POSITIVE_OR_ZERO("validation.positiveOrZero", "Must not be negative."),
    VALIDATION_DECIMAL_MAX("validation.decimalMax", "Too large.");

    private static final String VALIDATION_DOMAIN = "validation.";

    private static final Map<String, ErrorCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(value -> value.code, value -> value));

    private final String code;
    private final String detail;
    private final URI type;
    private final String[] argNames;

    ErrorCode(String code, String detail, String... argNames) {
        this(code, detail, null, argNames);
    }

    ErrorCode(String code, String detail, URI type, String... argNames) {
        this.code = code;
        this.detail = detail;
        this.type = type;
        this.argNames = argNames;
    }

    public String code() {
        return code;
    }

    /** The English, with a format specifier per argument name */
    public String detail() {
        return detail;
    }

    public URI type() {
        return type;
    }

    public int arity() {
        return argNames.length;
    }

    /** Formats the English and serves each value under its argument name, as a number or a string */
    public Formatted with(Object... values) {
        if (values.length != argNames.length) {
            throw new IllegalArgumentException("%s takes %d arguments, not %d"
                    .formatted(name(), argNames.length, values.length));
        }
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            args.put(argNames[i], values[i] instanceof Number ? values[i] : String.valueOf(values[i]));
        }
        return new Formatted(this, detail.formatted(values), Collections.unmodifiableMap(args));
    }

    public static ErrorCode forConstraint(String constraint) {
        if (constraint == null || constraint.isEmpty()) {
            return VALIDATION_INVALID;
        }
        String code = VALIDATION_DOMAIN + Character.toLowerCase(constraint.charAt(0)) + constraint.substring(1);
        return BY_CODE.getOrDefault(code, VALIDATION_INVALID);
    }

    public record Formatted(ErrorCode errorCode, String detail, Map<String, Object> args) {
    }
}
