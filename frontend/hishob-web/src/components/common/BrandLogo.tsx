import React from 'react';

export type BrandLogoVariant = 'full' | 'icon' | 'responsive';
export type BrandLogoSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'custom';

export interface BrandLogoProps {
  variant?: BrandLogoVariant;
  size?: BrandLogoSize;
  alt?: string;
  className?: string;
  priority?: boolean;
  style?: React.CSSProperties;
  height?: number | string;
  width?: number | string;
}

// Full logo aspect ratio: 1942 / 809 ≈ 2.40
const FULL_DIMENSIONS: Record<Exclude<BrandLogoSize, 'custom'>, { width: number; height: number }> = {
  xs: { width: 58, height: 24 },
  sm: { width: 86, height: 36 },
  md: { width: 110, height: 46 },
  lg: { width: 168, height: 70 },
  xl: { width: 240, height: 100 },
};

// Icon logo aspect ratio: 1254 / 1254 = 1.00
const ICON_DIMENSIONS: Record<Exclude<BrandLogoSize, 'custom'>, { width: number; height: number }> = {
  xs: { width: 20, height: 20 },
  sm: { width: 28, height: 28 },
  md: { width: 34, height: 34 },
  lg: { width: 48, height: 48 },
  xl: { width: 64, height: 64 },
};

const LOGO_PATHS = {
  full: '/branding/hishob-logo.png',
  icon: '/branding/hishob-icon.png',
} as const;

export default function BrandLogo({
  variant = 'full',
  size = 'md',
  alt,
  className = '',
  priority = false,
  style,
  height,
  width,
}: BrandLogoProps) {
  const defaultAlt =
    variant === 'icon' ? 'HISHOB' : 'HISHOB — Split, Track, Settle Together';
  const resolvedAlt = alt ?? defaultAlt;

  // Responsive mode: shows full logo on desktop (>=768px) and compact icon on mobile (<768px)
  if (variant === 'responsive') {
    const fullDim = size !== 'custom' ? FULL_DIMENSIONS[size] : null;
    const iconDim = size !== 'custom' ? ICON_DIMENSIONS[size] : null;

    return (
      <span className={`brand-logo brand-logo--responsive brand-logo--${size} ${className}`} style={{ display: 'inline-flex', alignItems: 'center', ...style }}>
        <img
          src={LOGO_PATHS.full}
          alt={resolvedAlt}
          width={width ?? fullDim?.width}
          height={height ?? fullDim?.height}
          loading={priority ? 'eager' : 'lazy'}
          decoding="async"
          className="brand-logo__img brand-logo__img--desktop"
          style={{
            objectFit: 'contain',
            aspectRatio: '1942 / 809',
            maxWidth: '100%',
            height: height ?? (fullDim ? `${fullDim.height}px` : 'auto'),
          }}
        />
        <img
          src={LOGO_PATHS.icon}
          alt={resolvedAlt}
          width={width ?? iconDim?.width}
          height={height ?? iconDim?.height}
          loading={priority ? 'eager' : 'lazy'}
          decoding="async"
          className="brand-logo__img brand-logo__img--mobile"
          style={{
            objectFit: 'contain',
            aspectRatio: '1 / 1',
            maxWidth: '100%',
            height: height ?? (iconDim ? `${iconDim.height}px` : 'auto'),
          }}
        />
      </span>
    );
  }

  const isFull = variant === 'full';
  const dimensions = isFull
    ? (size !== 'custom' ? FULL_DIMENSIONS[size] : null)
    : (size !== 'custom' ? ICON_DIMENSIONS[size] : null);

  const imgWidth = width ?? dimensions?.width;
  const imgHeight = height ?? dimensions?.height;
  const src = isFull ? LOGO_PATHS.full : LOGO_PATHS.icon;

  return (
    <img
      src={src}
      alt={resolvedAlt}
      width={imgWidth}
      height={imgHeight}
      loading={priority ? 'eager' : 'lazy'}
      decoding="async"
      style={{
        objectFit: 'contain',
        verticalAlign: 'middle',
        maxWidth: '100%',
        aspectRatio: isFull ? '1942 / 809' : '1 / 1',
        height: imgHeight ? `${imgHeight}px` : 'auto',
        ...style,
      }}
      className={`brand-logo brand-logo--${variant} brand-logo--${size} ${className}`}
    />
  );
}
