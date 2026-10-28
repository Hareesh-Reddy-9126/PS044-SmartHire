import { ACCOUNT_ROLES, ROLE_META, type AccountRole } from '../../utils/roles';

export interface RoleSelectorProps {
  value: AccountRole;
  onChange: (role: AccountRole) => void;
  /** Visible group label. */
  legend?: string;
  /** Roles offered by this selector. Defaults to every account role. */
  roles?: readonly AccountRole[];
  disabled?: boolean;
  className?: string;
}

/**
 * Shared account-role picker for sign-in and registration.
 *
 * This is a native radio group rather than a custom combobox: every offered
 * option is visible at once, arrow-key navigation works without JavaScript, and
 * a screen reader announces the group and the selected portal. The selected role
 * only chooses the intended workspace; the server-issued token remains the
 * authority on what the account can actually open.
 */
export function RoleSelector({
  value,
  onChange,
  legend = 'Continue as',
  roles = ACCOUNT_ROLES,
  disabled = false,
  className = '',
}: RoleSelectorProps) {
  return (
    <fieldset className={`role-selector ${className}`.trim()} disabled={disabled}>
      <legend className="role-selector-legend">{legend}</legend>

      <div className="role-selector-grid">
        {roles.map(role => {
          const meta = ROLE_META[role];
          const selected = value === role;

          return (
            <label
              key={role}
              className={`role-option ${selected ? 'is-selected' : ''}`.trim()}
              data-role={role}
            >
              <input
                type="radio"
                name="account-role"
                value={role}
                checked={selected}
                onChange={() => onChange(role)}
                className="sr-only"
              />
              <span className="role-option-icon material-symbols-outlined" aria-hidden="true">
                {meta.icon}
              </span>
              <span className="role-option-copy">
                <span className="role-option-label">{meta.label}</span>
                <span className="role-option-hint">{meta.hint}</span>
              </span>
              <span className="role-option-check material-symbols-outlined" aria-hidden="true">
                check_circle
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
