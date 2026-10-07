/**
 * FormField — accessible labeled input with optional error display.
 */

interface FormFieldProps {
  id: string;
  label: string;
  type?: string;
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  error?: string;
  autoComplete?: string;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
}

export default function FormField({
  id,
  label,
  type = 'text',
  value,
  onChange,
  error,
  autoComplete,
  placeholder,
  required,
  disabled,
}: FormFieldProps) {
  return (
    <div className="form-field">
      <label htmlFor={id} className="form-field__label">
        {label}
        {required && <span className="form-field__required" aria-hidden="true"> *</span>}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={onChange}
        className={`form-field__input${error ? ' form-field__input--error' : ''}`}
        autoComplete={autoComplete}
        placeholder={placeholder}
        required={required}
        disabled={disabled}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${id}-error` : undefined}
      />
      {error && (
        <p id={`${id}-error`} className="form-field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
