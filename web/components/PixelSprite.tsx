import { sprites, type SpriteName } from '@/lib/sprites'

/**
 * One of ALIVE's sprites, drawn the way Android draws it: whole cells on a 16
 * grid, no smoothing. `shape-rendering: crispEdges` is the whole point — a
 * scaled pixel face that gets anti-aliased stops being the same character.
 */
export default function PixelSprite({
  name,
  size,
  className,
  label
}: {
  name: SpriteName
  size: number
  className?: string
  label?: string
}) {
  const sprite = sprites[name]
  const decorative = !label

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${sprite.size} ${sprite.size}`}
      shapeRendering="crispEdges"
      className={className}
      role={decorative ? undefined : 'img'}
      aria-hidden={decorative || undefined}
      aria-label={label}
    >
      {sprite.layers.flatMap((layer, layerIndex) =>
        layer.cells.map(([x, y, w, h], cellIndex) => (
          <rect
            key={`${layerIndex}-${cellIndex}`}
            x={x}
            y={y}
            width={w}
            height={h}
            fill={layer.colour}
          />
        ))
      )}
    </svg>
  )
}
