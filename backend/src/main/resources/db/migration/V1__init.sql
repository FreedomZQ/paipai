-- AppHub / Paipai unified database initialization.
--
-- This file intentionally squashes the former V1..V19 Flyway migrations into a
-- single fresh-database baseline. Paipai is currently the first shipping app, so
-- keeping one initialization script avoids migration drift while preserving the
-- final schema, indexes, constraints, default app_code values, and seed configs
-- required by the current backend code.
--
-- Important: use this baseline for new databases. Existing databases that have
-- already applied the old multi-file migration chain need an explicit reset or
-- Flyway repair strategy before switching to this squashed baseline.

SET TIME ZONE 'UTC';
--
--


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: reading_announcement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_announcement (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    announcement_uuid character varying(64) NOT NULL,
    title character varying(256) NOT NULL,
    content text NOT NULL,
    status character varying(32) DEFAULT 'published'::character varying NOT NULL,
    visible_start_at timestamp with time zone NOT NULL,
    visible_end_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    announcement_type character varying(32),
    priority integer DEFAULT 0 NOT NULL,
    action_url character varying(512),
    action_text character varying(128),
    dismissible boolean DEFAULT true NOT NULL,
    max_display_count integer DEFAULT 1 NOT NULL,
    min_interval_seconds integer DEFAULT 86400 NOT NULL,
    trigger_scene character varying(64) DEFAULT 'app_launch'::character varying NOT NULL,
    target_locale character varying(32),
    target_plan_code character varying(64),
    target_min_app_version character varying(64),
    target_max_app_version character varying(64)
);


--
-- Name: reading_announcement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_announcement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_announcement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_announcement_id_seq OWNED BY public.reading_announcement.id;


--
-- Name: reading_child_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_child_profile (
    id character varying(64) NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    nickname character varying(128) NOT NULL,
    age_band character varying(32) NOT NULL,
    learning_track_code character varying(64) NOT NULL,
    avatar_emoji character varying(16) DEFAULT '🧸'::character varying NOT NULL,
    profile_status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    last_modified_by_installation_id character varying(64),
    record_version integer DEFAULT 1 NOT NULL
);


--
-- Name: reading_child_usage_daily; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_child_usage_daily (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    usage_date date NOT NULL,
    duration_seconds integer DEFAULT 0 NOT NULL,
    session_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_child_usage_daily_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_child_usage_daily_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_child_usage_daily_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_child_usage_daily_id_seq OWNED BY public.reading_child_usage_daily.id;


--
-- Name: reading_cloud_service_usage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_cloud_service_usage (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    service_type character varying(64) NOT NULL,
    trial_limit integer NOT NULL,
    trial_used integer DEFAULT 0 NOT NULL,
    purchased_credits integer DEFAULT 0 NOT NULL,
    purchased_used integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_cloud_service_usage_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_cloud_service_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_cloud_service_usage_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_cloud_service_usage_id_seq OWNED BY public.reading_cloud_service_usage.id;


--
-- Name: reading_daily_task_completion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_daily_task_completion (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64),
    task_id character varying(128) NOT NULL,
    completion_type character varying(64) NOT NULL,
    task_date date NOT NULL,
    completed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_daily_task_completion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_daily_task_completion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_daily_task_completion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_daily_task_completion_id_seq OWNED BY public.reading_daily_task_completion.id;


--
-- Name: reading_feedback_ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_feedback_ticket (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint,
    ticket_no character varying(64) NOT NULL,
    category character varying(64) NOT NULL,
    content text NOT NULL,
    contact_email character varying(256),
    auth_mode character varying(64),
    trace_id character varying(128),
    status character varying(32) DEFAULT 'open'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_feedback_ticket_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_feedback_ticket_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_feedback_ticket_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_feedback_ticket_id_seq OWNED BY public.reading_feedback_ticket.id;


--
-- Name: reading_ocr_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_ocr_audit (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint,
    trace_id character varying(128) NOT NULL,
    provider character varying(64) NOT NULL,
    model character varying(64) NOT NULL,
    status character varying(64) NOT NULL,
    note character varying(512),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_ocr_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_ocr_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_ocr_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_ocr_audit_id_seq OWNED BY public.reading_ocr_audit.id;


--
-- Name: reading_review_card; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_review_card (
    id character varying(64) NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    learning_track_code character varying(64) NOT NULL,
    encrypted_text text NOT NULL,
    text_preview character varying(256),
    support_hint character varying(512),
    proficiency integer DEFAULT 0 NOT NULL,
    next_review_at timestamp with time zone,
    sync_enabled boolean DEFAULT false NOT NULL,
    storage_mode character varying(32) DEFAULT 'server_authoritative'::character varying NOT NULL,
    card_status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    source_text text,
    translated_text text,
    source_language_code character varying(32),
    target_language_code character varying(32),
    source_type character varying(32),
    last_reviewed_at timestamp with time zone,
    deleted_at timestamp with time zone,
    last_modified_by_installation_id character varying(64),
    record_version integer DEFAULT 1 NOT NULL
);


--
-- Name: reading_review_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_review_event (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    card_id character varying(64) NOT NULL,
    event_type character varying(64) NOT NULL,
    result_level character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_review_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_review_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_review_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_review_event_id_seq OWNED BY public.reading_review_event.id;


--
-- Name: reading_review_event_v2; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_review_event_v2 (
    id character varying(64) NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    card_id character varying(64) NOT NULL,
    event_type character varying(32) NOT NULL,
    result_level character varying(32) NOT NULL,
    event_at timestamp with time zone NOT NULL,
    last_modified_by_installation_id character varying(64),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_usage_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_usage_session (
    id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    session_uuid character varying(64) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    duration_seconds integer DEFAULT 0 NOT NULL,
    client_platform character varying(32),
    device_model character varying(128),
    source_page character varying(64),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_usage_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reading_usage_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reading_usage_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reading_usage_session_id_seq OWNED BY public.reading_usage_session.id;


--
-- Name: reading_usage_session_v2; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_usage_session_v2 (
    id character varying(64) NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    source_page character varying(64) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    duration_seconds integer,
    client_platform character varying(32),
    device_model character varying(128),
    last_modified_by_installation_id character varying(64),
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: reading_user_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reading_user_preference (
    user_id bigint NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    ui_locale character varying(32),
    source_language_code character varying(32),
    target_language_code character varying(32),
    reading_track_code character varying(64),
    tts_voice_code character varying(64),
    translation_mode character varying(32),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cloud_sync_enabled boolean DEFAULT false NOT NULL,
    last_modified_by_installation_id character varying(64),
    record_version integer DEFAULT 1 NOT NULL
);


--
-- Name: sys_app; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_app (
    app_code character varying(64) NOT NULL,
    app_name character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_app_store_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_app_store_notification (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    notification_uuid character varying(128) NOT NULL,
    notification_type character varying(128),
    subtype character varying(128),
    signed_payload_hash character varying(128) NOT NULL,
    verification_status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    processing_status character varying(32) DEFAULT 'accepted'::character varying NOT NULL,
    raw_payload_json jsonb NOT NULL,
    received_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_app_store_notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_app_store_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_app_store_notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_app_store_notification_id_seq OWNED BY public.sys_app_store_notification.id;


--
-- Name: sys_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_audit_log (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    operator_type character varying(32) NOT NULL,
    operator_id character varying(128),
    action_code character varying(128) NOT NULL,
    trace_id character varying(128),
    payload_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_audit_log_id_seq OWNED BY public.sys_audit_log.id;


--
-- Name: sys_auth_provider_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_auth_provider_token (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    provider_code character varying(32) NOT NULL,
    provider_subject character varying(256) NOT NULL,
    refresh_token text,
    access_token text,
    token_type character varying(64),
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    payload_json jsonb,
    refresh_token_key_id character varying(64),
    refresh_token_encryption_algorithm character varying(64),
    refresh_token_nonce_base64 text,
    refresh_token_ciphertext_base64 text,
    refresh_token_last_captured_at timestamp with time zone,
    refresh_token_last_used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_auth_provider_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_auth_provider_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_auth_provider_token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_auth_provider_token_id_seq OWNED BY public.sys_auth_provider_token.id;


--
-- Name: sys_auth_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_auth_session (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    session_token_hash character varying(128) NOT NULL,
    session_source character varying(32) NOT NULL,
    device_id character varying(128),
    client_platform character varying(32),
    client_version character varying(64),
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    last_seen_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_auth_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_auth_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_auth_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_auth_session_id_seq OWNED BY public.sys_auth_session.id;


--
-- Name: sys_email_verification_ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_email_verification_ticket (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    email character varying(256) NOT NULL,
    scene_code character varying(64) NOT NULL,
    code_hash character varying(128) NOT NULL,
    status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempt_count integer DEFAULT 6 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    verified_at timestamp with time zone,
    consumed_at timestamp with time zone,
    request_ip character varying(64),
    payload_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_email_verification_ticket_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_email_verification_ticket_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_email_verification_ticket_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_email_verification_ticket_id_seq OWNED BY public.sys_email_verification_ticket.id;


--
-- Name: sys_entitlement_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_entitlement_snapshot (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    entitlement_code character varying(64) NOT NULL,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    source_type character varying(32) NOT NULL,
    expires_at timestamp with time zone,
    payload_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_entitlement_snapshot_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_entitlement_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_entitlement_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_entitlement_snapshot_id_seq OWNED BY public.sys_entitlement_snapshot.id;


--
-- Name: sys_purchase_transaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_purchase_transaction (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    source_type character varying(32) NOT NULL,
    product_id character varying(128),
    transaction_id character varying(128),
    original_transaction_id character varying(128) NOT NULL,
    store_environment character varying(32),
    storefront character varying(32),
    app_account_token character varying(128),
    signed_transaction_info_hash character varying(128) NOT NULL,
    signed_renewal_info_hash character varying(128),
    verification_status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    processing_status character varying(32) DEFAULT 'accepted'::character varying NOT NULL,
    payload_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_purchase_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_purchase_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_purchase_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_purchase_transaction_id_seq OWNED BY public.sys_purchase_transaction.id;


--
-- Name: sys_remote_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_remote_config (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    namespace_code character varying(64) NOT NULL,
    config_key character varying(128) NOT NULL,
    config_value_json jsonb NOT NULL,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_remote_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_remote_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_remote_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_remote_config_id_seq OWNED BY public.sys_remote_config.id;


--
-- Name: sys_sync_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_sync_audit_log (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    installation_id character varying(64),
    action_type character varying(64) NOT NULL,
    entity_type character varying(64),
    entity_id character varying(128),
    request_id character varying(128),
    result_status character varying(32) NOT NULL,
    detail_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_sync_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_sync_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_sync_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_sync_audit_log_id_seq OWNED BY public.sys_sync_audit_log.id;


--
-- Name: sys_sync_installation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_sync_installation (
    installation_id character varying(64) NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    device_id character varying(128),
    client_platform character varying(32) NOT NULL,
    device_model character varying(128),
    app_version character varying(64),
    powersync_client_id character varying(128),
    cloud_sync_enabled boolean DEFAULT false NOT NULL,
    initial_sync_completed boolean DEFAULT false NOT NULL,
    last_sync_at timestamp with time zone,
    last_pull_at timestamp with time zone,
    last_push_at timestamp with time zone,
    last_error_code character varying(64),
    last_error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_type character varying(32) NOT NULL,
    display_name character varying(128),
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_user_device_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_device_event (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint,
    session_id bigint,
    event_type character varying(64) NOT NULL,
    bundle_id character varying(128),
    client_platform character varying(32),
    device_model character varying(128),
    system_name character varying(64),
    system_version character varying(64),
    app_version character varying(64),
    build_number character varying(64),
    locale character varying(32),
    ip_country character varying(32),
    payload_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_user_device_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_device_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_device_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_device_event_id_seq OWNED BY public.sys_user_device_event.id;


--
-- Name: sys_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_id_seq OWNED BY public.sys_user.id;


--
-- Name: sys_user_identity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_identity (
    id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    provider_code character varying(32) NOT NULL,
    provider_subject character varying(256) NOT NULL,
    email character varying(256),
    email_verified boolean,
    private_email boolean,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    payload_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sys_user_identity_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_identity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_identity_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_identity_id_seq OWNED BY public.sys_user_identity.id;


--
-- Name: reading_announcement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_announcement ALTER COLUMN id SET DEFAULT nextval('public.reading_announcement_id_seq'::regclass);


--
-- Name: reading_child_usage_daily id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_usage_daily ALTER COLUMN id SET DEFAULT nextval('public.reading_child_usage_daily_id_seq'::regclass);


--
-- Name: reading_cloud_service_usage id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_cloud_service_usage ALTER COLUMN id SET DEFAULT nextval('public.reading_cloud_service_usage_id_seq'::regclass);


--
-- Name: reading_daily_task_completion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_daily_task_completion ALTER COLUMN id SET DEFAULT nextval('public.reading_daily_task_completion_id_seq'::regclass);


--
-- Name: reading_feedback_ticket id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_feedback_ticket ALTER COLUMN id SET DEFAULT nextval('public.reading_feedback_ticket_id_seq'::regclass);


--
-- Name: reading_ocr_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_ocr_audit ALTER COLUMN id SET DEFAULT nextval('public.reading_ocr_audit_id_seq'::regclass);


--
-- Name: reading_review_event id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event ALTER COLUMN id SET DEFAULT nextval('public.reading_review_event_id_seq'::regclass);


--
-- Name: reading_usage_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session ALTER COLUMN id SET DEFAULT nextval('public.reading_usage_session_id_seq'::regclass);


--
-- Name: sys_app_store_notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_app_store_notification ALTER COLUMN id SET DEFAULT nextval('public.sys_app_store_notification_id_seq'::regclass);


--
-- Name: sys_audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_audit_log ALTER COLUMN id SET DEFAULT nextval('public.sys_audit_log_id_seq'::regclass);


--
-- Name: sys_auth_provider_token id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_provider_token ALTER COLUMN id SET DEFAULT nextval('public.sys_auth_provider_token_id_seq'::regclass);


--
-- Name: sys_auth_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_session ALTER COLUMN id SET DEFAULT nextval('public.sys_auth_session_id_seq'::regclass);


--
-- Name: sys_email_verification_ticket id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_email_verification_ticket ALTER COLUMN id SET DEFAULT nextval('public.sys_email_verification_ticket_id_seq'::regclass);


--
-- Name: sys_entitlement_snapshot id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_entitlement_snapshot ALTER COLUMN id SET DEFAULT nextval('public.sys_entitlement_snapshot_id_seq'::regclass);


--
-- Name: sys_purchase_transaction id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_purchase_transaction ALTER COLUMN id SET DEFAULT nextval('public.sys_purchase_transaction_id_seq'::regclass);


--
-- Name: sys_remote_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_remote_config ALTER COLUMN id SET DEFAULT nextval('public.sys_remote_config_id_seq'::regclass);


--
-- Name: sys_sync_audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_sync_audit_log ALTER COLUMN id SET DEFAULT nextval('public.sys_sync_audit_log_id_seq'::regclass);


--
-- Name: sys_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user ALTER COLUMN id SET DEFAULT nextval('public.sys_user_id_seq'::regclass);


--
-- Name: sys_user_device_event id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_device_event ALTER COLUMN id SET DEFAULT nextval('public.sys_user_device_event_id_seq'::regclass);


--
-- Name: sys_user_identity id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_identity ALTER COLUMN id SET DEFAULT nextval('public.sys_user_identity_id_seq'::regclass);


--
-- Name: reading_announcement reading_announcement_announcement_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_announcement
    ADD CONSTRAINT reading_announcement_announcement_uuid_key UNIQUE (announcement_uuid);


--
-- Name: reading_announcement reading_announcement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_announcement
    ADD CONSTRAINT reading_announcement_pkey PRIMARY KEY (id);


--
-- Name: reading_child_profile reading_child_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_profile
    ADD CONSTRAINT reading_child_profile_pkey PRIMARY KEY (id);


--
-- Name: reading_child_usage_daily reading_child_usage_daily_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_usage_daily
    ADD CONSTRAINT reading_child_usage_daily_pkey PRIMARY KEY (id);


--
-- Name: reading_child_usage_daily reading_child_usage_daily_user_id_child_id_usage_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_usage_daily
    ADD CONSTRAINT reading_child_usage_daily_user_id_child_id_usage_date_key UNIQUE (user_id, child_id, usage_date);


--
-- Name: reading_cloud_service_usage reading_cloud_service_usage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_cloud_service_usage
    ADD CONSTRAINT reading_cloud_service_usage_pkey PRIMARY KEY (id);


--
-- Name: reading_cloud_service_usage reading_cloud_service_usage_user_id_service_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_cloud_service_usage
    ADD CONSTRAINT reading_cloud_service_usage_user_id_service_type_key UNIQUE (user_id, service_type);


--
-- Name: reading_daily_task_completion reading_daily_task_completion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_daily_task_completion
    ADD CONSTRAINT reading_daily_task_completion_pkey PRIMARY KEY (id);


--
-- Name: reading_daily_task_completion reading_daily_task_completion_user_id_task_id_task_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_daily_task_completion
    ADD CONSTRAINT reading_daily_task_completion_user_id_task_id_task_date_key UNIQUE (user_id, task_id, task_date);


--
-- Name: reading_feedback_ticket reading_feedback_ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_feedback_ticket
    ADD CONSTRAINT reading_feedback_ticket_pkey PRIMARY KEY (id);


--
-- Name: reading_feedback_ticket reading_feedback_ticket_ticket_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_feedback_ticket
    ADD CONSTRAINT reading_feedback_ticket_ticket_no_key UNIQUE (ticket_no);


--
-- Name: reading_ocr_audit reading_ocr_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_ocr_audit
    ADD CONSTRAINT reading_ocr_audit_pkey PRIMARY KEY (id);


--
-- Name: reading_ocr_audit reading_ocr_audit_trace_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_ocr_audit
    ADD CONSTRAINT reading_ocr_audit_trace_id_key UNIQUE (trace_id);


--
-- Name: reading_review_card reading_review_card_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_card
    ADD CONSTRAINT reading_review_card_pkey PRIMARY KEY (id);


--
-- Name: reading_review_event reading_review_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event
    ADD CONSTRAINT reading_review_event_pkey PRIMARY KEY (id);


--
-- Name: reading_review_event_v2 reading_review_event_v2_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event_v2
    ADD CONSTRAINT reading_review_event_v2_pkey PRIMARY KEY (id);


--
-- Name: reading_usage_session reading_usage_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session
    ADD CONSTRAINT reading_usage_session_pkey PRIMARY KEY (id);


--
-- Name: reading_usage_session reading_usage_session_session_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session
    ADD CONSTRAINT reading_usage_session_session_uuid_key UNIQUE (session_uuid);


--
-- Name: reading_usage_session_v2 reading_usage_session_v2_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session_v2
    ADD CONSTRAINT reading_usage_session_v2_pkey PRIMARY KEY (id);


--
-- Name: reading_user_preference reading_user_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_user_preference
    ADD CONSTRAINT reading_user_preference_pkey PRIMARY KEY (user_id);


--
-- Name: sys_app sys_app_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_app
    ADD CONSTRAINT sys_app_pkey PRIMARY KEY (app_code);


--
-- Name: sys_app_store_notification sys_app_store_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_app_store_notification
    ADD CONSTRAINT sys_app_store_notification_pkey PRIMARY KEY (id);


--
-- Name: sys_audit_log sys_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_audit_log
    ADD CONSTRAINT sys_audit_log_pkey PRIMARY KEY (id);


--
-- Name: sys_auth_provider_token sys_auth_provider_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_provider_token
    ADD CONSTRAINT sys_auth_provider_token_pkey PRIMARY KEY (id);


--
-- Name: sys_auth_session sys_auth_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_session
    ADD CONSTRAINT sys_auth_session_pkey PRIMARY KEY (id);


--
-- Name: sys_auth_session sys_auth_session_session_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_session
    ADD CONSTRAINT sys_auth_session_session_token_hash_key UNIQUE (session_token_hash);


--
-- Name: sys_email_verification_ticket sys_email_verification_ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_email_verification_ticket
    ADD CONSTRAINT sys_email_verification_ticket_pkey PRIMARY KEY (id);


--
-- Name: sys_entitlement_snapshot sys_entitlement_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_entitlement_snapshot
    ADD CONSTRAINT sys_entitlement_snapshot_pkey PRIMARY KEY (id);


--
-- Name: sys_purchase_transaction sys_purchase_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_purchase_transaction
    ADD CONSTRAINT sys_purchase_transaction_pkey PRIMARY KEY (id);


--
-- Name: sys_remote_config sys_remote_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_remote_config
    ADD CONSTRAINT sys_remote_config_pkey PRIMARY KEY (id);


--
-- Name: sys_sync_audit_log sys_sync_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_sync_audit_log
    ADD CONSTRAINT sys_sync_audit_log_pkey PRIMARY KEY (id);


--
-- Name: sys_sync_installation sys_sync_installation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_sync_installation
    ADD CONSTRAINT sys_sync_installation_pkey PRIMARY KEY (installation_id);


--
-- Name: sys_user_device_event sys_user_device_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_device_event
    ADD CONSTRAINT sys_user_device_event_pkey PRIMARY KEY (id);


--
-- Name: sys_user_identity sys_user_identity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_identity
    ADD CONSTRAINT sys_user_identity_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);


--
-- Name: idx_reading_announcement_status_window; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_announcement_status_window ON public.reading_announcement USING btree (status, visible_start_at DESC, visible_end_at DESC);


--
-- Name: idx_reading_child_profile_user_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_child_profile_user_deleted ON public.reading_child_profile USING btree (user_id, deleted_at);


--
-- Name: idx_reading_child_profile_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_child_profile_user_status ON public.reading_child_profile USING btree (user_id, profile_status, updated_at DESC);


--
-- Name: idx_reading_child_profile_user_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_child_profile_user_updated ON public.reading_child_profile USING btree (user_id, updated_at DESC);


--
-- Name: idx_reading_child_usage_daily_app_user_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_child_usage_daily_app_user_date ON public.reading_child_usage_daily USING btree (app_code, user_id, usage_date);


--
-- Name: idx_reading_child_usage_daily_user_child_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_child_usage_daily_user_child_date ON public.reading_child_usage_daily USING btree (user_id, child_id, usage_date DESC);


--
-- Name: idx_reading_daily_task_completion_user_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_daily_task_completion_user_date ON public.reading_daily_task_completion USING btree (user_id, task_date DESC);


--
-- Name: idx_reading_feedback_ticket_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_feedback_ticket_user_created ON public.reading_feedback_ticket USING btree (user_id, created_at DESC);


--
-- Name: idx_reading_ocr_audit_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_ocr_audit_user_created ON public.reading_ocr_audit USING btree (user_id, created_at DESC);


--
-- Name: idx_reading_review_card_child_status_next_review; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_card_child_status_next_review ON public.reading_review_card USING btree (child_id, card_status, next_review_at);


--
-- Name: idx_reading_review_card_child_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_card_child_updated ON public.reading_review_card USING btree (child_id, updated_at DESC);


--
-- Name: idx_reading_review_card_user_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_card_user_deleted ON public.reading_review_card USING btree (user_id, deleted_at);


--
-- Name: idx_reading_review_card_user_next_review; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_card_user_next_review ON public.reading_review_card USING btree (user_id, next_review_at);


--
-- Name: idx_reading_review_card_user_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_card_user_status_created ON public.reading_review_card USING btree (user_id, card_status, created_at DESC);


--
-- Name: idx_reading_review_card_user_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_card_user_updated ON public.reading_review_card USING btree (user_id, updated_at DESC);


--
-- Name: idx_reading_review_event_user_child_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_event_user_child_created ON public.reading_review_event USING btree (user_id, child_id, created_at DESC);


--
-- Name: idx_reading_review_event_v2_card_event_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_event_v2_card_event_at ON public.reading_review_event_v2 USING btree (card_id, event_at DESC);


--
-- Name: idx_reading_review_event_v2_child_event_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_event_v2_child_event_at ON public.reading_review_event_v2 USING btree (child_id, event_at DESC);


--
-- Name: idx_reading_review_event_v2_user_event_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_review_event_v2_user_event_at ON public.reading_review_event_v2 USING btree (user_id, event_at DESC);


--
-- Name: idx_reading_usage_session_child_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_usage_session_child_started ON public.reading_usage_session USING btree (child_id, started_at DESC);


--
-- Name: idx_reading_usage_session_user_child_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_usage_session_user_child_started ON public.reading_usage_session USING btree (user_id, child_id, started_at DESC);


--
-- Name: idx_reading_usage_session_v2_app_user_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_usage_session_v2_app_user_time ON public.reading_usage_session_v2 USING btree (app_code, user_id, COALESCE(ended_at, updated_at, started_at));


--
-- Name: idx_reading_usage_session_v2_child_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_usage_session_v2_child_started ON public.reading_usage_session_v2 USING btree (child_id, started_at DESC);


--
-- Name: idx_reading_usage_session_v2_user_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_usage_session_v2_user_deleted ON public.reading_usage_session_v2 USING btree (user_id, deleted_at);


--
-- Name: idx_reading_usage_session_v2_user_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reading_usage_session_v2_user_started ON public.reading_usage_session_v2 USING btree (user_id, started_at DESC);


--
-- Name: idx_sys_app_store_notification_app_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_app_store_notification_app_received ON public.sys_app_store_notification USING btree (app_code, received_at DESC);


--
-- Name: idx_sys_audit_log_app_action_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_audit_log_app_action_created ON public.sys_audit_log USING btree (app_code, action_code, created_at DESC);


--
-- Name: idx_sys_auth_provider_token_user_provider; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_auth_provider_token_user_provider ON public.sys_auth_provider_token USING btree (user_id, provider_code, updated_at DESC);


--
-- Name: idx_sys_auth_session_app_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_auth_session_app_status_created ON public.sys_auth_session USING btree (app_code, status, created_at DESC);


--
-- Name: idx_sys_auth_session_user_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_auth_session_user_status_created ON public.sys_auth_session USING btree (user_id, status, created_at DESC);


--
-- Name: idx_sys_email_verification_ticket_app_email_scene_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_email_verification_ticket_app_email_scene_created ON public.sys_email_verification_ticket USING btree (app_code, email, scene_code, created_at DESC);


--
-- Name: idx_sys_email_verification_ticket_app_status_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_email_verification_ticket_app_status_expires ON public.sys_email_verification_ticket USING btree (app_code, status, expires_at);


--
-- Name: idx_sys_entitlement_snapshot_user_status_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_entitlement_snapshot_user_status_updated ON public.sys_entitlement_snapshot USING btree (app_code, user_id, status, updated_at DESC);


--
-- Name: idx_sys_purchase_transaction_app_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_purchase_transaction_app_user_created ON public.sys_purchase_transaction USING btree (app_code, user_id, created_at DESC);


--
-- Name: idx_sys_purchase_transaction_original_tx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_purchase_transaction_original_tx ON public.sys_purchase_transaction USING btree (app_code, original_transaction_id, created_at DESC);


--
-- Name: idx_sys_remote_config_app_namespace_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_remote_config_app_namespace_key ON public.sys_remote_config USING btree (app_code, namespace_code, config_key);


--
-- Name: idx_sys_sync_audit_log_installation_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_sync_audit_log_installation_created ON public.sys_sync_audit_log USING btree (installation_id, created_at DESC);


--
-- Name: idx_sys_sync_audit_log_user_app_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_sync_audit_log_user_app_created ON public.sys_sync_audit_log USING btree (user_id, app_code, created_at DESC);


--
-- Name: idx_sys_sync_installation_app_sync; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_sync_installation_app_sync ON public.sys_sync_installation USING btree (app_code, cloud_sync_enabled, updated_at DESC);


--
-- Name: idx_sys_sync_installation_user_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_sync_installation_user_app ON public.sys_sync_installation USING btree (user_id, app_code);


--
-- Name: idx_sys_user_app_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_user_app_status_created ON public.sys_user USING btree (app_code, status, created_at DESC);


--
-- Name: idx_sys_user_device_event_app_event_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_user_device_event_app_event_created ON public.sys_user_device_event USING btree (app_code, event_type, created_at DESC);


--
-- Name: idx_sys_user_device_event_app_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_user_device_event_app_user_created ON public.sys_user_device_event USING btree (app_code, user_id, created_at DESC);


--
-- Name: idx_sys_user_identity_user_provider; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_user_identity_user_provider ON public.sys_user_identity USING btree (user_id, provider_code, updated_at DESC);


--
-- Name: uk_reading_cloud_service_usage_user_service; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_reading_cloud_service_usage_user_service ON public.reading_cloud_service_usage USING btree (user_id, service_type);


--
-- Name: uk_sys_app_store_notification_app_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_app_store_notification_app_uuid ON public.sys_app_store_notification USING btree (app_code, notification_uuid);


--
-- Name: uk_sys_auth_provider_token_identity; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_auth_provider_token_identity ON public.sys_auth_provider_token USING btree (app_code, provider_code, provider_subject);


--
-- Name: uk_sys_user_identity_app_provider_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sys_user_identity_app_provider_subject ON public.sys_user_identity USING btree (app_code, provider_code, provider_subject);


--
-- Name: uq_reading_user_preference_app_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reading_user_preference_app_user ON public.reading_user_preference USING btree (app_code, user_id);


--
-- Name: uq_sys_sync_installation_app_user_installation; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_sys_sync_installation_app_user_installation ON public.sys_sync_installation USING btree (app_code, user_id, installation_id);


--
-- Name: reading_child_profile reading_child_profile_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_profile
    ADD CONSTRAINT reading_child_profile_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_child_usage_daily reading_child_usage_daily_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_usage_daily
    ADD CONSTRAINT reading_child_usage_daily_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id);


--
-- Name: reading_child_usage_daily reading_child_usage_daily_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_child_usage_daily
    ADD CONSTRAINT reading_child_usage_daily_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_cloud_service_usage reading_cloud_service_usage_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_cloud_service_usage
    ADD CONSTRAINT reading_cloud_service_usage_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_daily_task_completion reading_daily_task_completion_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_daily_task_completion
    ADD CONSTRAINT reading_daily_task_completion_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_feedback_ticket reading_feedback_ticket_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_feedback_ticket
    ADD CONSTRAINT reading_feedback_ticket_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_ocr_audit reading_ocr_audit_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_ocr_audit
    ADD CONSTRAINT reading_ocr_audit_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_review_card reading_review_card_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_card
    ADD CONSTRAINT reading_review_card_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id);


--
-- Name: reading_review_card reading_review_card_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_card
    ADD CONSTRAINT reading_review_card_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_review_event reading_review_event_card_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event
    ADD CONSTRAINT reading_review_event_card_id_fkey FOREIGN KEY (card_id) REFERENCES public.reading_review_card(id);


--
-- Name: reading_review_event reading_review_event_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event
    ADD CONSTRAINT reading_review_event_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id);


--
-- Name: reading_review_event reading_review_event_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event
    ADD CONSTRAINT reading_review_event_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_review_event_v2 reading_review_event_v2_card_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event_v2
    ADD CONSTRAINT reading_review_event_v2_card_id_fkey FOREIGN KEY (card_id) REFERENCES public.reading_review_card(id);


--
-- Name: reading_review_event_v2 reading_review_event_v2_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event_v2
    ADD CONSTRAINT reading_review_event_v2_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id);


--
-- Name: reading_review_event_v2 reading_review_event_v2_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_review_event_v2
    ADD CONSTRAINT reading_review_event_v2_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_usage_session reading_usage_session_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session
    ADD CONSTRAINT reading_usage_session_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id);


--
-- Name: reading_usage_session reading_usage_session_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session
    ADD CONSTRAINT reading_usage_session_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_usage_session_v2 reading_usage_session_v2_child_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session_v2
    ADD CONSTRAINT reading_usage_session_v2_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id);


--
-- Name: reading_usage_session_v2 reading_usage_session_v2_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_usage_session_v2
    ADD CONSTRAINT reading_usage_session_v2_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: reading_user_preference reading_user_preference_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reading_user_preference
    ADD CONSTRAINT reading_user_preference_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_auth_provider_token sys_auth_provider_token_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_provider_token
    ADD CONSTRAINT sys_auth_provider_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_auth_session sys_auth_session_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_auth_session
    ADD CONSTRAINT sys_auth_session_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_entitlement_snapshot sys_entitlement_snapshot_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_entitlement_snapshot
    ADD CONSTRAINT sys_entitlement_snapshot_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_purchase_transaction sys_purchase_transaction_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_purchase_transaction
    ADD CONSTRAINT sys_purchase_transaction_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_sync_audit_log sys_sync_audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_sync_audit_log
    ADD CONSTRAINT sys_sync_audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_sync_installation sys_sync_installation_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_sync_installation
    ADD CONSTRAINT sys_sync_installation_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: sys_user_identity sys_user_identity_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_identity
    ADD CONSTRAINT sys_user_identity_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
--


-- =========================================================
-- Seed data
-- =========================================================

--
--


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: reading_announcement; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.reading_announcement (id, app_code, announcement_uuid, title, content, status, visible_start_at, visible_end_at, created_at, updated_at, announcement_type, priority, action_url, action_text, dismissible, max_display_count, min_interval_seconds, trigger_scene, target_locale, target_plan_code, target_min_app_version, target_max_app_version) VALUES (1, 'paipai_readingcompanion', 'support-center-sample-20260422', '支持中心联调公告', '这是一条用于联调的支持中心公告，只会在支持与隐私页按场景、语言、版本和套餐命中后展示。', 'published', '2026-04-22 00:00:00+00', '2026-06-30 23:59:59+00', '2026-04-24 10:48:46.24271+00', '2026-04-24 10:48:46.254931+00', 'info', 5, 'https://www.paipai.app/help', '查看帮助', true, 1, 0, 'support_center', 'zh-Hans', 'family_multi_child_lifetime', '1.0.0', NULL);
INSERT INTO public.reading_announcement (id, app_code, announcement_uuid, title, content, status, visible_start_at, visible_end_at, created_at, updated_at, announcement_type, priority, action_url, action_text, dismissible, max_display_count, min_interval_seconds, trigger_scene, target_locale, target_plan_code, target_min_app_version, target_max_app_version) VALUES (2, 'paipai_readingcompanion', 'paipai-p1-launch-safe-start-20260424', '首发使用小提示', '拍拍伴读首发推荐每天从一句开始：拍一句、听一听、保存成句卡，明天回来复习。学习内容默认优先保存在本机；云同步由家长主动开启；家庭版权益与扣款金额以 App 内展示和 Apple 确认弹窗为准。', 'published', '2026-04-24 00:00:00+00', '2026-07-31 23:59:59+00', '2026-04-24 10:48:46.322486+00', '2026-04-24 10:48:46.322486+00', 'info', 20, NULL, NULL, true, 1, 86400, 'app_launch', 'zh-Hans', NULL, '1.0.0', NULL);
INSERT INTO public.reading_announcement (id, app_code, announcement_uuid, title, content, status, visible_start_at, visible_end_at, created_at, updated_at, announcement_type, priority, action_url, action_text, dismissible, max_display_count, min_interval_seconds, trigger_scene, target_locale, target_plan_code, target_min_app_version, target_max_app_version) VALUES (3, 'paipai_readingcompanion', 'paipai-p1-support-privacy-boundary-20260424', '家长安心说明', '相机和相册仅用于拍摄或选择绘本内容；账号删除、隐私政策、服务条款和儿童数据说明都可在 App 内查看。若遇到识别、朗读或购买恢复问题，请先保留当前 Apple 账号与订单状态，再联系 support@paipai.app。', 'published', '2026-04-24 00:00:00+00', '2026-07-31 23:59:59+00', '2026-04-24 10:48:46.326806+00', '2026-04-24 10:48:46.326806+00', 'info', 10, NULL, NULL, true, 2, 604800, 'support_center', 'zh-Hans', NULL, '1.0.0', NULL);


--
-- Data for Name: sys_user; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_child_profile; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_child_usage_daily; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_cloud_service_usage; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_daily_task_completion; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_feedback_ticket; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_ocr_audit; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_review_card; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_review_event; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_review_event_v2; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_usage_session; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_usage_session_v2; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: reading_user_preference; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_app; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.sys_app (app_code, app_name, status, created_at, updated_at) VALUES ('paipai_readingcompanion', '拍拍伴读', 'active', '2026-04-24 10:48:45.582053+00', '2026-04-24 10:48:46.197828+00');
INSERT INTO public.sys_app (app_code, app_name, status, created_at, updated_at) VALUES ('saving', '省钱项目', 'active', '2026-04-24 10:48:45.582053+00', '2026-04-24 10:48:46.197828+00');


--
-- Data for Name: sys_app_store_notification; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_auth_provider_token; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_auth_session; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_email_verification_ticket; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_entitlement_snapshot; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_purchase_transaction; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_remote_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (6, 'saving', 'billing_refresh_policy', 'candidateLimit', '{"value": 20}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (10, 'saving', 'bootstrap', 'supported_locales', '{"value": ["zh-Hans", "en", "es"]}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (12, 'saving', 'features', 'advanced_report_enabled', '{"value": true}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (22, 'saving', 'billing_entitlements', 'productMappings.pro_yearly', '{"value": "pro_access"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (30, 'saving', 'bootstrap', 'default_locale', '{"value": "zh-Hans"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (34, 'saving', 'billing_refresh_policy', 'cooldownMinutes', '{"value": 5}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (35, 'saving', 'bootstrap', 'recommended_plan_code', '{"value": "pro_yearly"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (17, 'paipai_readingcompanion', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.monthly', '{"value": "family_access"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (16, 'paipai_readingcompanion', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.multi_child.lifetime', '{"value": "family_multi_child"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (11, 'paipai_readingcompanion', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.yearly', '{"value": "family_access"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (5, 'paipai_readingcompanion', 'billing_refresh_policy', 'candidateLimit', '{"value": 20}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (32, 'paipai_readingcompanion', 'billing_refresh_policy', 'cooldownMinutes', '{"value": 5}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (28, 'paipai_readingcompanion', 'bootstrap', 'default_locale', '{"value": "zh-Hans"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (26, 'paipai_readingcompanion', 'bootstrap', 'paywall_default_highlight', '{"value": "family_multi_child_lifetime"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (9, 'paipai_readingcompanion', 'bootstrap', 'supported_locales', '{"value": ["zh-Hans", "en"]}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (8, 'paipai_readingcompanion', 'cloud_provider', 'ocr.apiKeyEnvName', '{"value": "DASHSCOPE_API_KEY"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (21, 'paipai_readingcompanion', 'cloud_provider', 'ocr.endpoint', '{"value": "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (23, 'paipai_readingcompanion', 'cloud_provider', 'ocr.headers', ('{"value": {"Content-Type": "application/json", "Authorization": "Bearer ' || '$' || '{API_KEY}"}}')::jsonb, 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (20, 'paipai_readingcompanion', 'cloud_provider', 'ocr.maxPixels', '{"value": 8388608}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (19, 'paipai_readingcompanion', 'cloud_provider', 'ocr.minPixels', '{"value": 3072}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (29, 'paipai_readingcompanion', 'cloud_provider', 'ocr.model', '{"value": "qwen-vl-ocr-latest"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (33, 'paipai_readingcompanion', 'cloud_provider', 'ocr.prompt', '{"value": "Please output only the text content from the image without any additional descriptions or formatting."}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (37, 'paipai_readingcompanion', 'cloud_provider', 'ocr.region', '{"value": "singapore"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (1, 'paipai_readingcompanion', 'cloud_provider', 'ocr.vendor', '{"value": "alibaba_bailian"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (36, 'paipai_readingcompanion', 'cloud_provider', 'tts.apiKeyEnvName', '{"value": "DASHSCOPE_API_KEY"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (13, 'paipai_readingcompanion', 'cloud_provider', 'tts.format', '{"value": "mp3"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (27, 'paipai_readingcompanion', 'cloud_provider', 'tts.headers', ('{"value": {"Authorization": "Bearer ' || '$' || '{API_KEY}"}}')::jsonb, 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (24, 'paipai_readingcompanion', 'cloud_provider', 'tts.model', '{"value": "cosyvoice-v3-flash"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (7, 'paipai_readingcompanion', 'cloud_provider', 'tts.pitch', '{"value": 1.0}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (31, 'paipai_readingcompanion', 'cloud_provider', 'tts.rate', '{"value": 1.0}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (3, 'paipai_readingcompanion', 'cloud_provider', 'tts.region', '{"value": "singapore"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (14, 'paipai_readingcompanion', 'cloud_provider', 'tts.sampleRate', '{"value": 22050}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (4, 'paipai_readingcompanion', 'cloud_provider', 'tts.vendor', '{"value": "alibaba_bailian"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (25, 'paipai_readingcompanion', 'cloud_provider', 'tts.voice', '{"value": "longanyang"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (18, 'paipai_readingcompanion', 'cloud_provider', 'tts.volume', '{"value": 50}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (2, 'paipai_readingcompanion', 'cloud_provider', 'tts.wsUrl', '{"value": "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference"}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (15, 'paipai_readingcompanion', 'features', 'cloud_sync_enabled', '{"value": false}', 'active', '2026-04-24 10:48:45.587336+00', '2026-04-24 10:48:45.587336+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (44, 'paipai_readingcompanion', 'reading_language_catalog', 'learning_tracks', '{"value": [{"code": "zh_to_en", "label": "中文家庭学英语"}, {"code": "en_to_zh", "label": "English families learn Chinese"}]}', 'active', '2026-04-24 10:48:45.807929+00', '2026-04-24 10:48:45.807929+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (45, 'paipai_readingcompanion', 'reading_language_catalog', 'supported_locales', '{"value": ["zh-Hans", "en"]}', 'active', '2026-04-24 10:48:45.807929+00', '2026-04-24 10:48:45.807929+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (39, 'paipai_readingcompanion', 'reading_language_catalog', 'supported_source_languages', '{"value": ["en", "zh-Hans"]}', 'active', '2026-04-24 10:48:45.80329+00', '2026-04-24 10:48:45.80329+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (43, 'paipai_readingcompanion', 'reading_language_catalog', 'supported_target_languages', '{"value": ["zh-Hans", "en"]}', 'active', '2026-04-24 10:48:45.80329+00', '2026-04-24 10:48:45.80329+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (46, 'paipai_readingcompanion', 'reading_plan_catalog', 'free', '{"value": {"code": "free", "childLimit": 1, "displayName": "免费版", "premiumActive": false, "dailyPlanScope": "single_child", "historyEnabled": false, "localCardLimit": 20, "entitlementCode": "free", "cloudSyncEnabled": false, "dailyCaptureLimit": 3, "multiChildEnabled": false, "weeklyReportScope": "child", "advancedVoiceEnabled": false, "customReminderEnabled": false, "weeklyReportHistoryWeeks": 0}}', 'active', '2026-04-24 10:48:45.807929+00', '2026-04-24 10:48:45.807929+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (41, 'paipai_readingcompanion', 'release_ios', 'current_project_version', '{"value": "1"}', 'active', '2026-04-24 10:48:45.80329+00', '2026-04-24 10:48:45.80329+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (42, 'paipai_readingcompanion', 'release_ios', 'development_team', '{"value": "__FILL_ME__"}', 'active', '2026-04-24 10:48:45.80329+00', '2026-04-24 10:48:45.80329+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (40, 'paipai_readingcompanion', 'release_ios', 'marketing_version', '{"value": "1.0.0"}', 'active', '2026-04-24 10:48:45.80329+00', '2026-04-24 10:48:45.80329+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (38, 'paipai_readingcompanion', 'release_ios', 'paipai_api_base_url', '{"value": "https://api.paipai.app"}', 'active', '2026-04-24 10:48:45.80329+00', '2026-04-24 10:48:45.80329+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (47, 'paipai_readingcompanion', 'reading_plan_catalog', 'family_multi_child_lifetime', '{"value": {"code": "family_multi_child_lifetime", "badgeText": "一次开通", "highlight": true, "childLimit": 5, "displayName": "家庭多孩子终身版", "displayPrice": "¥68", "originalPrice": "¥98", "premiumActive": true, "dailyPlanScope": "per_child", "historyEnabled": true, "localCardLimit": 800, "entitlementCode": "family_multi_child", "cloudSyncEnabled": true, "appStoreProductId": "com.paipai.readalong.family.multi_child.lifetime", "dailyCaptureLimit": 50, "matchedProductIds": ["com.paipai.readalong.family.yearly", "com.paipai.readalong.family.monthly", "com.paipai.readalong.family.multi_child.lifetime"], "multiChildEnabled": true, "weeklyReportScope": "family", "advancedVoiceEnabled": true, "customReminderEnabled": false, "matchedEntitlementCodes": ["family_access", "family_multi_child"], "weeklyReportHistoryWeeks": 12}}', 'active', '2026-04-24 10:48:45.807929+00', '2026-04-24 10:48:46.249883+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (48, 'paipai_readingcompanion', 'release_ios', 'bundle_identifier', '{"value": "com.paipai.readalong.v2"}', 'active', '2026-04-24 10:48:46.28857+00', '2026-04-24 10:48:46.28857+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (51, 'paipai_readingcompanion', 'release_ios', 'first_release_positioning', '{"value": "首发版本仅承诺 iOS/iPadOS 18.0+；设备端翻译以 iOS 18 系统能力为准；iOS 17.x 不作为首发支持范围。"}', 'active', '2026-04-24 10:48:46.299468+00', '2026-04-24 10:48:46.299468+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (52, 'paipai_readingcompanion', 'release_ios', 'low_risk_review_notes', '{"value": "首发按本地 OCR、本地朗读、Apple 登录、App 内购买、账号删除、可选云 OCR/TTS 与云同步进行审核说明；不承诺未验证的 iOS 17.x 设备端翻译能力。"}', 'active', '2026-04-24 10:48:46.302258+00', '2026-04-24 10:48:46.302258+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (53, 'paipai_readingcompanion', 'release_ios', 'multi_app_expansion_note', '{"value": "统一后端以后续 app_code 为隔离边界；新增 App 应独立配置 app-definition、release_ios、billing_entitlements、cloud_provider、legal docs 与 PowerSync sync-rules。"}', 'active', '2026-04-24 10:48:46.305622+00', '2026-04-24 10:48:46.305622+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (49, 'paipai_readingcompanion', 'release_ios', 'minimum_ios_version', '{"value": "18.0"}', 'active', '2026-04-24 10:48:46.293368+00', '2026-04-24 10:48:46.308668+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (50, 'paipai_readingcompanion', 'release_ios', 'minimum_ipados_version', '{"value": "18.0"}', 'active', '2026-04-24 10:48:46.296355+00', '2026-04-24 10:48:46.314604+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (54, 'paipai_readingcompanion', 'reading_paywall_growth', 'default', '{"headline": "解锁家庭伴读节奏", "subtitle": "多孩子档案、更多拍读额度、云同步和周报历史，帮助家长长期看到孩子的进步。", "legalNotice": "权益以后端校验结果为准；价格与扣款以 Apple 确认弹窗为准。", "trialEnabled": false, "trustBullets": ["一次开通当前家庭版权益，具体扣款以 Apple 确认弹窗为准。", "学习内容默认优先保存在本机；云同步由家长主动开启。", "账号删除、法务文档和客服入口均在 App 内可访问。"], "defaultHighlight": "family_multi_child_lifetime"}', 'active', '2026-04-24 10:48:46.319188+00', '2026-04-24 10:48:46.319188+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (55, 'paipai_readingcompanion', 'reading_usage_policy', 'retentionDays', '{"value": 30}', 'active', '2026-04-24 10:48:46.332218+00', '2026-04-24 10:48:46.332218+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (56, 'paipai_readingcompanion', 'reading_usage_policy', 'recentSummaryDays', '{"value": 7}', 'active', '2026-04-24 10:48:46.335634+00', '2026-04-24 10:48:46.335634+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (57, 'paipai_readingcompanion', 'reading_usage_policy', 'dayBoundary', '{"value": "client_local"}', 'active', '2026-04-24 10:48:46.338934+00', '2026-04-24 10:48:46.338934+00');
INSERT INTO public.sys_remote_config (id, app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at) VALUES (58, 'paipai_readingcompanion', 'reading_usage_policy', 'maxSessionHours', '{"value": 24}', 'active', '2026-04-24 10:48:46.341315+00', '2026-04-24 10:48:46.341315+00');


--
-- Data for Name: sys_sync_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_sync_installation; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_user_device_event; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Data for Name: sys_user_identity; Type: TABLE DATA; Schema: public; Owner: -
--


--
-- Name: reading_announcement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_announcement_id_seq', 3, true);


--
-- Name: reading_child_usage_daily_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_child_usage_daily_id_seq', 1, false);


--
-- Name: reading_cloud_service_usage_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_cloud_service_usage_id_seq', 1, false);


--
-- Name: reading_daily_task_completion_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_daily_task_completion_id_seq', 1, false);


--
-- Name: reading_feedback_ticket_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_feedback_ticket_id_seq', 1, false);


--
-- Name: reading_ocr_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_ocr_audit_id_seq', 1, false);


--
-- Name: reading_review_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_review_event_id_seq', 1, false);


--
-- Name: reading_usage_session_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reading_usage_session_id_seq', 1, false);


--
-- Name: sys_app_store_notification_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_app_store_notification_id_seq', 1, false);


--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_audit_log_id_seq', 1, false);


--
-- Name: sys_auth_provider_token_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_auth_provider_token_id_seq', 1, false);


--
-- Name: sys_auth_session_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_auth_session_id_seq', 1, false);


--
-- Name: sys_email_verification_ticket_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_email_verification_ticket_id_seq', 1, false);


--
-- Name: sys_entitlement_snapshot_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_entitlement_snapshot_id_seq', 1, false);


--
-- Name: sys_purchase_transaction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_purchase_transaction_id_seq', 1, false);


--
-- Name: sys_remote_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_remote_config_id_seq', 58, true);


--
-- Name: sys_sync_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_sync_audit_log_id_seq', 1, false);


--
-- Name: sys_user_device_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_user_device_event_id_seq', 1, false);


--
-- Name: sys_user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_user_id_seq', 1, false);


--
-- Name: sys_user_identity_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sys_user_identity_id_seq', 1, false);


--
--
