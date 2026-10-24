import React, { forwardRef, useEffect, useId, useRef, useState } from 'react';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  helperText?: string;
  /** Option list; `children` is still supported for grouped options. */
  options?: SelectOption[];
  placeholder?: string;
  /** Smaller control for pagination and dense toolbars. */
  compact?: boolean;
}

type MenuPlacement = 'top' | 'bottom';

/**
 * A select control that uses the SmartHire popup instead of the operating
 * system's blue menu. The native select remains in the DOM as the form value
 * and compatibility surface; the visible control is an accessible combobox.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  {
    label,
    error,
    helperText,
    options,
    placeholder,
    compact = false,
    className = '',
    id,
    required,
    children,
    value,
    defaultValue,
    onChange,
    onBlur,
    onClick,
    onKeyDown,
    disabled,
    name,
    ...rest
  },
  ref
) {
  const generatedId = useId();
  const selectId = id || `select-${generatedId}`;
  const labelId = `${selectId}-label`;
  const errorId = `${selectId}-error`;
  const helperId = `${selectId}-helper`;
  const listboxId = `${selectId}-listbox`;
  const triggerId = `${selectId}-trigger`;
  const describedBy = [error ? errorId : null, helperText && !error ? helperId : null]
    .filter(Boolean)
    .join(' ');

  const wrapperRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listboxRef = useRef<HTMLDivElement>(null);

  const isControlled = value !== undefined;
  const [uncontrolledValue, setUncontrolledValue] = useState(() =>
    defaultValue == null ? '' : String(defaultValue)
  );
  const currentValue = isControlled ? String(value ?? '') : uncontrolledValue;

  const menuOptions: SelectOption[] = [];
  if (placeholder) menuOptions.push({ value: '', label: placeholder });
  if (options) menuOptions.push(...options);
  else if (children) menuOptions.push(...optionsFromChildren(children));

  const selectedIndex = menuOptions.findIndex(option => option.value === currentValue);
  const selectedOption = selectedIndex >= 0 ? menuOptions[selectedIndex] : undefined;
  const displayLabel =
    selectedOption?.label || currentValue || placeholder || 'Select an option';

  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [placement, setPlacement] = useState<MenuPlacement>('bottom');

  const setNativeRef = (node: HTMLSelectElement | null) => {
    if (typeof ref === 'function') ref(node);
    else if (ref) ref.current = node;
  };

  const firstEnabledIndex = () => menuOptions.findIndex(option => !option.disabled);

  const openMenu = () => {
    if (disabled) return;

    const trigger = triggerRef.current;
    if (trigger) {
      const rect = trigger.getBoundingClientRect();
      const desiredHeight = Math.min(menuOptions.length * 42 + 12, 288);
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;
      setPlacement(spaceBelow < desiredHeight && spaceAbove > spaceBelow ? 'top' : 'bottom');
    }

    setOpen(true);
    const nextIndex =
      selectedIndex >= 0 && !menuOptions[selectedIndex]?.disabled
        ? selectedIndex
        : firstEnabledIndex();
    setActiveIndex(nextIndex);
  };

  const closeMenu = (restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) triggerRef.current?.focus();
  };

  const emitChange = (nextValue: string) => {
    if (!isControlled) setUncontrolledValue(nextValue);

    if (onChange) {
      const target = { value: nextValue, name };
      const event = {
        target,
        currentTarget: target,
        preventDefault: () => undefined,
        stopPropagation: () => undefined,
      } as unknown as React.ChangeEvent<HTMLSelectElement>;
      onChange(event);
    }
  };

  const chooseOption = (option: SelectOption) => {
    if (option.disabled) return;
    emitChange(option.value);
    closeMenu(true);
  };

  const moveActive = (delta: number) => {
    if (menuOptions.length === 0) return;

    let next = activeIndex;
    for (let step = 0; step < menuOptions.length; step += 1) {
      next = (next + delta + menuOptions.length) % menuOptions.length;
      if (!menuOptions[next]?.disabled) {
        setActiveIndex(next);
        return;
      }
    }
  };

  const moveToEdge = (fromEnd: boolean) => {
    if (fromEnd) {
      for (let index = menuOptions.length - 1; index >= 0; index -= 1) {
        if (!menuOptions[index]?.disabled) {
          setActiveIndex(index);
          return;
        }
      }
      return;
    }

    const first = firstEnabledIndex();
    if (first >= 0) setActiveIndex(first);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    onKeyDown?.(event as unknown as React.KeyboardEvent<HTMLSelectElement>);
    if (event.defaultPrevented || disabled) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!open) openMenu();
        else moveActive(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (!open) openMenu();
        else moveActive(-1);
        break;
      case 'Home':
        if (open) {
          event.preventDefault();
          moveToEdge(false);
        }
        break;
      case 'End':
        if (open) {
          event.preventDefault();
          moveToEdge(true);
        }
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        if (open) {
          const option = menuOptions[activeIndex];
          if (option) chooseOption(option);
        } else {
          openMenu();
        }
        break;
      case 'Escape':
        if (open) {
          event.preventDefault();
          closeMenu(true);
        }
        break;
      case 'Tab':
        closeMenu();
        break;
      default:
        if (event.key.length === 1 && !event.metaKey && !event.ctrlKey && !event.altKey) {
          const query = event.key.toLowerCase();
          const match = menuOptions.findIndex(
            (option, index) =>
              index !== activeIndex &&
              !option.disabled &&
              option.label.toLowerCase().startsWith(query)
          );
          if (match >= 0) {
            if (!open) setOpen(true);
            setActiveIndex(match);
          }
        }
        break;
    }
  };

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) setOpen(false);
    };

    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [open]);

  useEffect(() => {
    if (!open || activeIndex < 0) return;
    const active = listboxRef.current?.querySelector<HTMLElement>(
      `[data-option-index="${activeIndex}"]`
    );
    active?.scrollIntoView?.({ block: 'nearest' });
  }, [open, activeIndex]);

  useEffect(() => {
    if (disabled) setOpen(false);
  }, [disabled]);

  return (
    <div
      ref={wrapperRef}
      className={['select-field', compact ? 'select-field-sm' : 'w-full', className]
        .filter(Boolean)
        .join(' ')}
    >
      {label && (
        <label
          id={labelId}
          htmlFor={triggerId}
          className={`label ${required ? 'label-required' : ''}`}
        >
          {label}
        </label>
      )}

      {/* Keeps native form/test semantics without showing the OS popup. */}
      <select
        ref={setNativeRef}
        id={selectId}
        value={currentValue}
        onChange={event => {
          if (!isControlled) setUncontrolledValue(event.target.value);
          onChange?.(event);
        }}
        disabled={disabled}
        required={required}
        tabIndex={-1}
        hidden
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options?.map(option => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
        {children}
      </select>

      {name && <input type="hidden" name={name} value={currentValue} />}

      <button
        ref={triggerRef}
        {...(rest as React.ButtonHTMLAttributes<HTMLButtonElement>)}
        id={triggerId}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-labelledby={label ? labelId : undefined}
        aria-describedby={describedBy || undefined}
        aria-invalid={error ? true : undefined}
        aria-required={required || undefined}
        aria-activedescendant={
          open && activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined
        }
        disabled={disabled}
        onClick={event => {
          onClick?.(event as unknown as React.MouseEvent<HTMLSelectElement>);
          if (event.defaultPrevented || disabled) return;
          if (open) closeMenu();
          else openMenu();
        }}
        onKeyDown={handleKeyDown}
        onBlur={event => {
          onBlur?.(event as unknown as React.FocusEvent<HTMLSelectElement>);
          const next = event.relatedTarget as Node | null;
          if (!next || !wrapperRef.current?.contains(next)) setOpen(false);
        }}
        className={[
          'select-trigger',
          compact ? 'select-trigger-sm' : '',
          error ? 'select-trigger-error' : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <span className={['select-value', selectedOption ? '' : 'select-placeholder'].filter(Boolean).join(' ')}>
          {displayLabel}
        </span>
        <span className="material-symbols-outlined select-chevron" aria-hidden="true">
          expand_more
        </span>
      </button>

      {open && (
        <div
          ref={listboxRef}
          id={listboxId}
          role="listbox"
          aria-labelledby={label ? labelId : undefined}
          data-placement={placement}
          className={['select-menu', compact ? 'select-menu-sm' : ''].filter(Boolean).join(' ')}
        >
          {menuOptions.length === 0 ? (
            <p className="select-menu-empty">No options available</p>
          ) : (
            menuOptions.map((option, index) => {
              const isSelected = option.value === currentValue;
              const isActive = index === activeIndex;

              return (
                <div
                  key={`${option.value}-${index}`}
                  id={`${listboxId}-option-${index}`}
                  role="option"
                  aria-selected={isSelected}
                  aria-disabled={option.disabled || undefined}
                  data-option-index={index}
                  data-active={isActive || undefined}
                  className={[
                    'select-option',
                    isSelected ? 'select-option-selected' : '',
                    option.disabled ? 'select-option-disabled' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onMouseDown={event => event.preventDefault()}
                  onMouseEnter={() => {
                    if (!option.disabled) setActiveIndex(index);
                  }}
                  onClick={() => chooseOption(option)}
                >
                  <span>{option.label}</span>
                  {isSelected && (
                    <span className="material-symbols-outlined select-option-check" aria-hidden="true">
                      check
                    </span>
                  )}
                </div>
              );
            })
          )}
        </div>
      )}

      {error && (
        <p id={errorId} className="error-text" role="alert">
          <span className="material-symbols-outlined text-[14px] shrink-0" aria-hidden="true">
            error
          </span>
          {error}
        </p>
      )}

      {helperText && !error && (
        <p id={helperId} className="helper-text">
          {helperText}
        </p>
      )}
    </div>
  );
});

function optionsFromChildren(children: React.ReactNode): SelectOption[] {
  const result: SelectOption[] = [];

  React.Children.forEach(children, child => {
    if (!React.isValidElement(child)) return;

    if (child.type === 'option') {
      const props = child.props as {
        value?: string | number;
        disabled?: boolean;
        children?: React.ReactNode;
      };
      const label = textOf(props.children);
      result.push({
        value: props.value == null ? label : String(props.value),
        label,
        disabled: props.disabled,
      });
      return;
    }

    if (child.type === 'optgroup') {
      const props = child.props as { children?: React.ReactNode };
      result.push(...optionsFromChildren(props.children));
    }
  });

  return result;
}

function textOf(node: React.ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (React.isValidElement(node)) {
    return textOf((node.props as { children?: React.ReactNode }).children);
  }
  return '';
}
