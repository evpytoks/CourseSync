using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddCourseContacts : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "contacts",
                table: "courses",
                type: "character varying(2000)",
                maxLength: 2000,
                nullable: false,
                defaultValue: "");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "contacts",
                table: "courses");
        }
    }
}
