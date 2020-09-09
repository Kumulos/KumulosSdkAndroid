package com.kumulos.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Patterns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents a checkin for a group of people at a location
 */
public final class KumulosCheckin {
    @SuppressLint("ConstantLocale")
    private static final SimpleDateFormat ISO8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

    private static final String FIELD_ID = "id";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_CONTACTS = "contacts";
    private static final String FIELD_PARTY_SIZE = "partySize";
    private static final String FIELD_CHECKED_IN_AT = "checkedInAt";
    private static final String FIELD_CHECKED_OUT_AT = "checkedOutAt";

    private int id = 0;

    @NonNull
    private String location;
    @NonNull
    private List<Contact> contacts = new ArrayList<>(4);
    private int partySize = 0;

    @Nullable
    private Date checkedInAt;
    @Nullable
    private Date checkedOutAt;

    private KumulosCheckin() {
        location = "unknown";
    }

    private KumulosCheckin(String location) {
        this.location = location;
    }

    /**
     * Create a checkin model for the given location
     *
     * @param identity Unique identifier for the location
     * @return Checkin model
     * @throws ValidationException
     */
    public static KumulosCheckin atLocation(@NonNull String identity) throws ValidationException {
        if (TextUtils.isEmpty(identity)) {
            throw new ValidationException("Location identity is required");
        }

        KumulosCheckin checkin = new KumulosCheckin(identity);

        return checkin;
    }

    /**
     * Add a contact to this checkin model
     * <p>
     * At least one contact is required to make the check in call.
     *
     * @param contact Contact to add to this checkin
     * @return
     */
    public KumulosCheckin addContact(@NonNull Contact contact) {
        this.contacts.add(contact);

        return this;
    }

    /**
     * Sets the number of people in the group for this checkin
     * <p>
     * Must be at least the number of contacts added to the checkin, but can be greater.
     *
     * @param number Total group size (named & un-named contacts)
     * @return
     * @throws ValidationException
     */
    public KumulosCheckin setPartySize(int number) throws ValidationException {
        if (number < 1 || number < contacts.size()) {
            throw new ValidationException("number must be greater than 0 and at least equal to the number of contacts for this check-in");
        }

        partySize = number;

        return this;
    }

    /**
     * Unique ID for this checkin
     * <p>
     * Available after creation through the {{@link KumulosCheckinClient#checkIn(Context, KumulosCheckin, Kumulos.ResultCallback)}} method
     *
     * @return
     */
    public int getId() {
        return id;
    }

    @Nullable
    public Date getCheckedInAt() {
        return checkedInAt;
    }

    @Nullable
    public Date getCheckedOutAt() {
        return checkedOutAt;
    }

    @NonNull
    public String getLocation() {
        return location;
    }

    @NonNull
    public List<Contact> getContacts() {
        return contacts;
    }

    /**
     * Returns the number of people considered part of the checkin party
     * <p>
     * If party size is explicitly set, returns that number. Otherwise, the number of added contacts
     * is used.
     *
     * @return
     */
    public int getPartySize() {
        if (0 == partySize) {
            return contacts.size();
        }

        return partySize;
    }

    JSONObject toJSONObject() throws JSONException {
        JSONObject props = new JSONObject();
        props.put(FIELD_LOCATION, getLocation());
        props.put(FIELD_PARTY_SIZE, getPartySize());

        JSONArray jsonContacts = new JSONArray();
        for (Contact contact : getContacts()) {
            jsonContacts.put(contact.toJSONObject());
        }

        props.put(FIELD_CONTACTS, jsonContacts);

        return props;
    }

    static KumulosCheckin fromJSONObject(JSONObject props) throws JSONException {
        KumulosCheckin checkin = new KumulosCheckin(props.getString(FIELD_LOCATION));

        if (props.has(FIELD_ID)) {
            checkin.id = props.getInt(FIELD_ID);
        }

        checkin.partySize = props.getInt(FIELD_PARTY_SIZE);

        if (props.has(FIELD_CHECKED_IN_AT) && !props.isNull(FIELD_CHECKED_IN_AT)) {
            try {
                checkin.checkedInAt = ISO8601_FORMAT.parse(props.getString(FIELD_CHECKED_IN_AT));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        if (props.has(FIELD_CHECKED_OUT_AT) && !props.isNull(FIELD_CHECKED_OUT_AT)) {
            try {
                checkin.checkedOutAt = ISO8601_FORMAT.parse(props.getString(FIELD_CHECKED_OUT_AT));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        JSONArray contactsArray = props.getJSONArray(FIELD_CONTACTS);
        List<Contact> contacts = new ArrayList<>(contactsArray.length());

        for (int i = 0; i < contactsArray.length(); ++i) {
            JSONObject object = contactsArray.getJSONObject(i);
            Contact contact = Contact.fromJSONObject(object);
            contacts.add(contact);
        }

        checkin.contacts = contacts;

        return checkin;
    }

    /**
     * Represents a named contact included in a checkin to a location
     */
    public static final class Contact {

        private static final String FIELD_ID = "id";
        private static final String FIELD_CHECKIN_ID = "checkinId";
        private static final String FIELD_FIRST_NAME = "firstName";
        private static final String FIELD_LAST_NAME = "lastName";
        private static final String FIELD_EMAIL_ADDRESS = "emailAddress";
        private static final String FIELD_SMS_NUMBER = "smsNumber";
        private static final String FIELD_META = "meta";
        private static final String FIELD_CHECKED_OUT_AT = "checkedOutAt";

        private int id = 0;
        private int checkinId = 0;

        @Nullable
        private String firstName;
        @NonNull
        private String lastName = "";
        @Nullable
        private String emailAddress;
        @Nullable
        private String smsNumber;
        @Nullable
        private Map<String, String> meta;

        @Nullable
        private Date checkedOutAt;

        private Contact() {
        }

        /**
         * Creates a contact model with the given last name
         * <p>
         * Whilst last name is the only required parameter, typically you should add further contact
         * details such as email or SMS number to the contact prior to check in.
         *
         * @param lastName
         * @return
         * @throws ValidationException
         */
        public static Contact withName(String lastName) throws ValidationException {
            return Contact.withName(lastName, null);
        }

        public static Contact withName(String lastName, @Nullable String firstName) throws ValidationException {
            if (TextUtils.isEmpty(lastName)) {
                throw new ValidationException("lastName must not be empty");
            }

            if (TextUtils.isEmpty(firstName)) {
                firstName = null;
            }

            Contact contact = new Contact();
            contact.firstName = firstName;
            contact.lastName = lastName;

            return contact;
        }

        public Contact setSmsNumber(@NonNull String number) throws ValidationException {
            if (TextUtils.isEmpty(number)) {
                throw new ValidationException("number must not be empty");
            }

            // TODO validation?

            this.smsNumber = number;

            return this;
        }

        public Contact setEmailAddress(@NonNull String emailAddress) throws ValidationException {
            if (TextUtils.isEmpty(emailAddress)) {
                throw new ValidationException("emailAddress must not be empty");
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()) {
                throw new ValidationException("emailAddress is not valid");
            }

            this.emailAddress = emailAddress;

            return this;
        }

        /**
         * Allows associating arbitrary meta-data with this contact
         *
         * @param meta
         * @return
         */
        public Contact setMeta(@NonNull Map<String, String> meta) {
            this.meta = meta;

            return this;
        }

        public int getId() {
            return id;
        }

        public int getCheckinId() {
            return checkinId;
        }

        @Nullable
        public String getFirstName() {
            return firstName;
        }

        @NonNull
        public String getLastName() {
            return lastName;
        }

        @Nullable
        public String getEmailAddress() {
            return emailAddress;
        }

        @Nullable
        public String getSmsNumber() {
            return smsNumber;
        }

        @Nullable
        public Map<String, String> getMeta() {
            return meta;
        }

        @Nullable
        public Date getCheckedOutAt() {
            return checkedOutAt;
        }

        public boolean isCheckedIn() {
            return 0 != this.id && null == this.checkedOutAt;
        }

        protected JSONObject toJSONObject() throws JSONException {
            JSONObject object = new JSONObject();
            object.put(FIELD_LAST_NAME, lastName);

            if (!TextUtils.isEmpty(firstName)) {
                object.put(FIELD_FIRST_NAME, firstName);
            }

            if (!TextUtils.isEmpty(emailAddress)) {
                object.put(FIELD_EMAIL_ADDRESS, emailAddress);
            }

            if (!TextUtils.isEmpty(smsNumber)) {
                object.put(FIELD_SMS_NUMBER, smsNumber);
            }

            if (null != meta) {
                object.put(FIELD_META, new JSONObject(meta));
            }

            return object;
        }

        static Contact fromJSONObject(JSONObject json) throws JSONException {
            String firstName = (!json.has(FIELD_FIRST_NAME) || json.isNull(FIELD_FIRST_NAME))
                    ? null
                    : json.getString(FIELD_FIRST_NAME);

            Contact contact = new Contact();
            contact.lastName = json.getString(FIELD_LAST_NAME);
            contact.firstName = firstName;

            if (json.has(FIELD_ID)) {
                contact.id = json.getInt(FIELD_ID);
            }

            if (json.has(FIELD_CHECKIN_ID)) {
                contact.checkinId = json.getInt(FIELD_CHECKIN_ID);
            }

            if (!json.isNull(FIELD_EMAIL_ADDRESS)) {
                contact.emailAddress = json.getString(FIELD_EMAIL_ADDRESS);
            }

            if (!json.isNull(FIELD_SMS_NUMBER)) {
                contact.smsNumber = json.getString(FIELD_SMS_NUMBER);
            }

            if (!json.isNull(FIELD_META)) {
                JSONObject jsonMeta = json.getJSONObject(FIELD_META);
                Map<String, String> meta = new HashMap<>(jsonMeta.length());

                for (Iterator<String> it = jsonMeta.keys(); it.hasNext(); ) {
                    String key = it.next();
                    meta.put(key, jsonMeta.getString(key));
                }

                contact.setMeta(meta);
            }

            if (json.has(FIELD_CHECKED_OUT_AT) && !json.isNull(FIELD_CHECKED_OUT_AT)) {
                try {
                    contact.checkedOutAt = ISO8601_FORMAT.parse(json.getString(FIELD_CHECKED_OUT_AT));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            return contact;
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

}
